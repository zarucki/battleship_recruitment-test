package org.zarucki.rest
import java.util.UUID

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Route
import com.softwaremill.session.{HeaderConfig, SessionConfig, SessionManager}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.ExecutionContext

class GameRoutingSpec extends BaseRouteSpec {
  val headerName = "Set-Auth-Token"
  val testSecret64characterLong = "x" * 64
  val uuidPickedAtRandom = UUID.randomUUID()
  val gameId = 17

  val routes = Route.seal(new GameRouting with StrictLogging {
    override implicit def sessionManager: SessionManager[Session] =
      new SessionManager[Session](
        SessionConfig
          .defaultConfig(testSecret64characterLong)
          .copy(sessionHeaderConfig = HeaderConfig(headerName, headerName))
      )
    override implicit def executor: ExecutionContext = executor
    override implicit def sessionCreator: SessionCreator = new SessionCreator {
      override def getNewSession(): Session = Session(uuidPickedAtRandom)
    }
  }.routes)

  it should "set header when sent POST to /game" in {
    Post("/game") ~> routes ~> check {
      header(headerName).map(extractSession).value shouldEqual uuidPickedAtRandom.toString
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual "new game"
    }
  }

  it should "asking for game state with session" in {
    withValidSession(1) { addSessionTransform =>
      Get(s"/game/$gameId") ~> addSessionTransform ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "game state"
      }
    }
  }

  it should "asking for game state without session should result in Forbidden code" in {
    Get(s"/game/$gameId") ~> routes ~> check {
      status shouldEqual StatusCodes.Forbidden
    }
  }

  private def extractSession(httpHeader: HttpHeader): String = {
    val headerAsString = httpHeader.toString()
    headerAsString.dropWhile(_ != '~').drop(1)
  }

  private def withValidSession(gameId: Int)(body: RequestTransformer => Unit) =
    Post("/game") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      val sessionHeader = header(headerName)
      sessionHeader.isDefined shouldEqual true
      body(addHeader(sessionHeader.get))
    }
}
