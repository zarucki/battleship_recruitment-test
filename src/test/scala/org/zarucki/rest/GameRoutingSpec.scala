package org.zarucki.rest
import java.util.UUID

import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Route
import com.softwaremill.session.{HeaderConfig, SessionConfig, SessionManager}
import com.typesafe.scalalogging.StrictLogging
import com.softwaremill.session.SessionOptions._
import org.zarucki.rest.BattleshipSession.UniqueId

import scala.concurrent.ExecutionContext

class GameRoutingSpec extends BaseRouteSpec {
  val headerName = "Set-Auth-Token"
  val testExecutor = executor
  val testSecret64characterLong = "x" * 64
  val player1UUIDPickedAtRandom = UUID.randomUUID()
  val player2UUIDPickedAtRandom = UUID.randomUUID()
  val gameUUIDPickedAtRandom = UUID.randomUUID()

  implicit val testSessionManager =
    new SessionManager[BattleshipSession](
      SessionConfig
        .defaultConfig(testSecret64characterLong)
        .copy(sessionHeaderConfig = HeaderConfig(headerName, headerName))
    )

  val routes = Route.seal(new GameRouting with StrictLogging {
    override implicit def sessionManager: SessionManager[BattleshipSession] = testSessionManager
    override implicit def executor: ExecutionContext = testExecutor
    override implicit def sessionCreator: SessionCreator = new SessionCreator {
      override def newSession(): BattleshipSession =
        BattleshipSession(playerId = player1UUIDPickedAtRandom, gameId = gameUUIDPickedAtRandom)
      override def newSessionForGame(gameId: UniqueId): BattleshipSession =
        BattleshipSession(playerId = player2UUIDPickedAtRandom, gameId = gameId)
    }
  }.routes)

  it should "set correct header when sent POST to /game" in {
    Post("/game") ~> routes ~> check {
      header(headerName).flatMap(extractSession).value shouldEqual BattleshipSession(
        playerId = player1UUIDPickedAtRandom,
        gameId = gameUUIDPickedAtRandom
      )
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual "new game"
    }
  }

  it should "return another valid session for given game when joining not full game" in {
    Post(s"/game/$gameUUIDPickedAtRandom/join") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      header(headerName).flatMap(extractSession).value shouldEqual BattleshipSession(
        playerId = player2UUIDPickedAtRandom,
        gameId = gameUUIDPickedAtRandom
      )
      responseAs[String] shouldEqual "game state"
    }
  }

  it should "return game state with correct session" in {
    withValidSession(1) { addSessionTransform =>
      Get(s"/game/$gameUUIDPickedAtRandom") ~> addSessionTransform ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "game state"
      }
    }
  }

  it should "return forbidden if session game id does not match url game id" in {
    withValidSession(1) { addSessionTransform =>
      Get(s"/game/${UUID.randomUUID()}") ~> addSessionTransform ~> routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }
  }

  it should "return forbidden if missing required session" in {
    Get(s"/game/$gameUUIDPickedAtRandom") ~> routes ~> check {
      status shouldEqual StatusCodes.Forbidden
    }
  }

  private def extractSession(httpHeader: HttpHeader): Option[BattleshipSession] =
    oneOff.clientSessionManager.decode(httpHeader.value()).toOption

  private def withValidSession(gameId: Int)(body: RequestTransformer => Unit) =
    Post("/game") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      val sessionHeader = header(headerName)
      sessionHeader.isDefined shouldEqual true
      body(addHeader(sessionHeader.get))
    }
}
