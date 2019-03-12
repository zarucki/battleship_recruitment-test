package org.zarucki.rest
import java.util.UUID

import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Route
import com.softwaremill.session.{HeaderConfig, SessionConfig, SessionManager}
import com.typesafe.scalalogging.StrictLogging
import com.softwaremill.session.SessionOptions._

import scala.concurrent.ExecutionContext

class GameRoutingSpec extends BaseRouteSpec {
  val headerName = "Set-Auth-Token"
  val testExecutor = executor
  val testSecret64characterLong = "x" * 64
  val userUuidPickedAtRandom = UUID.randomUUID()
  val gameUuidPickedAtRandom = UUID.randomUUID()
  implicit val testSessionManager =
    new SessionManager[Session](
      SessionConfig
        .defaultConfig(testSecret64characterLong)
        .copy(sessionHeaderConfig = HeaderConfig(headerName, headerName))
    )

  val routes = Route.seal(new GameRouting with StrictLogging {
    override implicit def sessionManager: SessionManager[Session] = testSessionManager
    override implicit def executor: ExecutionContext = testExecutor
    override implicit def sessionCreator: SessionCreator = new SessionCreator {
      override def newSession(): Session = Session(userId = userUuidPickedAtRandom, gameId = gameUuidPickedAtRandom)
    }
  }.routes)

  it should "set correct header when sent POST to /game" in {
    Post("/game") ~> routes ~> check {
      header(headerName).flatMap(extractSession).value shouldEqual Session(
        userId = userUuidPickedAtRandom,
        gameId = gameUuidPickedAtRandom
      )
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual "new game"
    }
  }

  it should "return game state with correct session" in {
    withValidSession(1) { addSessionTransform =>
      Get(s"/game/$gameUuidPickedAtRandom") ~> addSessionTransform ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "game state"
      }
    }
  }

  it should "return error if session game id does not match url game id" in {
    withValidSession(1) { addSessionTransform =>
      Get(s"/game/${UUID.randomUUID()}") ~> addSessionTransform ~> routes ~> check {
        status shouldEqual StatusCodes.Forbidden
        responseAs[String] shouldEqual "Game id not matching session."
      }
    }
  }

  it should "return Forbidden if missing required session" in {
    Get(s"/game/$gameUuidPickedAtRandom") ~> routes ~> check {
      status shouldEqual StatusCodes.Forbidden
    }
  }

  private def extractSession(httpHeader: HttpHeader): Option[Session] =
    oneOff.clientSessionManager.decode(httpHeader.value()).toOption

  private def withValidSession(gameId: Int)(body: RequestTransformer => Unit) =
    Post("/game") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      val sessionHeader = header(headerName)
      sessionHeader.isDefined shouldEqual true
      body(addHeader(sessionHeader.get))
    }
}
