package org.zarucki.rest

import java.util.UUID

import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Route
import com.softwaremill.session.{HeaderConfig, SessionConfig, SessionManager}
import com.typesafe.scalalogging.StrictLogging
import com.softwaremill.session.SessionOptions._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.scalatest.BeforeAndAfterEach
import org.zarucki.UniqueId
import org.zarucki.game.{GameStateStore, InMemoryGameStateStore}

import scala.concurrent.ExecutionContext

class GameRoutingSpec extends BaseRouteSpec with BeforeAndAfterEach {
  val headerName = "Set-Auth-Token"
  val serverHostName = "battleship-game.com"
  val serverPort = 8080
  val testExecutor = executor
  val testSecret64characterLong = "x" * 64
  val player1UUIDPickedAtRandom = UUID.randomUUID()
  val player2UUIDPickedAtRandom = UUID.randomUUID()
  val gameUUIDPickedAtRandom = UUID.randomUUID()
  val testGameStateStore = new InMemoryGameStateStore[TwoPlayersGameState]()

  override protected def afterEach(): Unit =
    testGameStateStore.clear()

  implicit val testSessionManager =
    new SessionManager[GameSession](
      SessionConfig
        .defaultConfig(testSecret64characterLong)
        .copy(sessionHeaderConfig = HeaderConfig(headerName, headerName))
    )

  val routes = Route.seal(new GameRouting with StrictLogging {
    override implicit val sessionManager: SessionManager[GameSession] = testSessionManager
    override implicit val executor: ExecutionContext = testExecutor
    override implicit val sessionCreator: SessionCreator = new SessionCreator {
      override def newSession(): GameSession =
        GameSession(playerId = player1UUIDPickedAtRandom, gameId = gameUUIDPickedAtRandom)
      override def newSessionForGame(gameId: UniqueId): GameSession =
        GameSession(playerId = player2UUIDPickedAtRandom, gameId = gameId)
    }
    override val gameStateStore: GameStateStore[TwoPlayersGameState] = testGameStateStore
  }.routes)

  it should "set correct header when sent POST to /game" in {
    Post("/game") ~> Host(serverHostName, serverPort) ~> routes ~> check {
      header(headerName).flatMap(extractSession).value shouldEqual GameSession(
        playerId = player1UUIDPickedAtRandom,
        gameId = gameUUIDPickedAtRandom
      )

      status shouldEqual StatusCodes.OK
      entityAs[GameInvitation] shouldEqual GameInvitation(
        s"http://$serverHostName:$serverPort/game/$gameUUIDPickedAtRandom/join"
      )

      testGameStateStore.getGameById(gameUUIDPickedAtRandom).value shouldEqual TwoPlayersGameState(
        hostPlayerId = player1UUIDPickedAtRandom,
        otherPlayerId = None
      )
    }
  }

  it should "return not found if game was not created before and we try to join it" in {
    Post(s"/game/$gameUUIDPickedAtRandom/join") ~> routes ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  it should "return another valid session for given game when joining not full game" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$gameUUIDPickedAtRandom/join") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        header(headerName).flatMap(extractSession).value shouldEqual GameSession(
          playerId = player2UUIDPickedAtRandom,
          gameId = gameUUIDPickedAtRandom
        )
        responseAs[String] shouldEqual "game state"
      }
    }
  }

  it should "return forbidden if game already full" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$gameUUIDPickedAtRandom/join") ~> routes ~> check {
        Post(s"/game/$gameUUIDPickedAtRandom/join") ~> routes ~> check {
          status shouldEqual StatusCodes.Forbidden
          responseAs[String] shouldEqual "Game already full."
        }
      }
    }
  }

  it should "return game state with correct session" in {
    createGameAndGetValidSession { addSessionTransform =>
      Get(s"/game/$gameUUIDPickedAtRandom") ~> addSessionTransform ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "game state"
      }
    }
  }

  it should "return forbidden if session game id does not match url game id" in {
    createGameAndGetValidSession { addSessionTransform =>
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

  private def extractSession(httpHeader: HttpHeader): Option[GameSession] =
    oneOff.clientSessionManager.decode(httpHeader.value()).toOption

  private def createGameAndGetValidSession(body: RequestTransformer => Unit) =
    Post("/game") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      val sessionHeader = header(headerName)
      sessionHeader.isDefined shouldEqual true
      body(addHeader(sessionHeader.get))
    }
}
