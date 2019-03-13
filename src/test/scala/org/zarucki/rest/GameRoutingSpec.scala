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
  val (player1UUIDPickedAtRandom, player2UUIDPickedAtRandom) = (UUID.randomUUID(), UUID.randomUUID())
  val (game1UUIDPickedAtRandom, game2UUIDPickedAtRandom) = (UUID.randomUUID(), UUID.randomUUID())
  var gameIdsToGive: List[UniqueId] = _
  var userIdsToGive: List[UniqueId] = _

  val testGameStateStore = new InMemoryGameStateStore[TwoPlayersGameState] {
    override def saveNewGame(newGame: TwoPlayersGameState): UniqueId = {
      val newGameId = gameIdsToGive.head
      gameIdsToGive = gameIdsToGive.tail
      concurrentStorage.put(newGameId, newGame)
      newGameId
    }
  }

  override protected def beforeEach(): Unit = {
    gameIdsToGive = List(game1UUIDPickedAtRandom, game2UUIDPickedAtRandom, UUID.randomUUID())
    userIdsToGive = List(player1UUIDPickedAtRandom, player2UUIDPickedAtRandom, UUID.randomUUID())
  }

  override protected def afterEach(): Unit =
    testGameStateStore.clear()

  implicit val testSessionManager =
    new SessionManager[UserSession](
      SessionConfig
        .defaultConfig(testSecret64characterLong)
        .copy(sessionHeaderConfig = HeaderConfig(headerName, headerName))
    )

  val routes = Route.seal(new GameRouting with StrictLogging {
    override implicit val sessionManager: SessionManager[UserSession] = testSessionManager
    override implicit val executor: ExecutionContext = testExecutor
    override implicit val sessionCreator: SessionCreator = new SessionCreator {
      override def newSession(): UserSession = {
        val newUserId = userIdsToGive.head
        userIdsToGive = userIdsToGive.tail
        UserSession(userId = newUserId)
      }
    }
    override val gameStateStore: GameStateStore[UniqueId, TwoPlayersGameState] = testGameStateStore
  }.routes)

  it should "set correct header when sent POST to /game" in {
    Post("/game") ~> Host(serverHostName, serverPort) ~> routes ~> check {
      header(headerName).flatMap(extractSession).value shouldEqual UserSession(
        userId = player1UUIDPickedAtRandom
      )

      status shouldEqual StatusCodes.OK
      entityAs[GameInvitation] shouldEqual GameInvitation(
        s"http://$serverHostName:$serverPort/game/$game1UUIDPickedAtRandom/join"
      )

      testGameStateStore.getGameById(game1UUIDPickedAtRandom).value shouldEqual TwoPlayersGameState(
        hostPlayerId = player1UUIDPickedAtRandom,
        otherPlayerId = None
      )
    }
  }

  it should "return not found if game was not created before and we try to join it" in {
    Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  it should "return already in game for a player that hosted game if he tries to join it" in {
    createGameAndGetValidSession { addSessionTransform =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> addSessionTransform ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Already in game."
      }
    }
  }

  it should "join player to another game and not set new session, if he is already in some other game" in {
    createGameAndGetValidSession { firstGameSessionTransform =>
      createGameAndGetValidSession { _ =>
        Post(s"/game/$game2UUIDPickedAtRandom/join") ~> firstGameSessionTransform ~> routes ~> check {
          status shouldEqual StatusCodes.OK
          header(headerName).isEmpty shouldEqual true
          responseAs[String] shouldEqual s"game state $game2UUIDPickedAtRandom"
        }
      }
    }
  }

  it should "return valid session for another player, given game when joining not full game" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        header(headerName).flatMap(extractSession).value shouldEqual UserSession(
          userId = player2UUIDPickedAtRandom,
        )
        responseAs[String] shouldEqual s"game state $game1UUIDPickedAtRandom"
      }
    }
  }

  it should "return forbidden if game already full" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
          status shouldEqual StatusCodes.Forbidden
          responseAs[String] shouldEqual "Game already full."
        }
      }
    }
  }

  it should "return game state with correct session" in {
    createGameAndGetValidSession { addSessionTransform =>
      Get(s"/game/$game1UUIDPickedAtRandom") ~> addSessionTransform ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "game state"
      }
    }
  }

  it should "return not found when trying to access non existing game" in {
    createGameAndGetValidSession { addSessionTransform =>
      Get(s"/game/${UUID.randomUUID()}") ~> addSessionTransform ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
  }

  it should "return forbidden when trying to access game you are not part of" in {
    createGameAndGetValidSession { firstGameSessionTransform =>
      createGameAndGetValidSession { _ =>
        Get(s"/game/$game2UUIDPickedAtRandom") ~> firstGameSessionTransform ~> routes ~> check {
          status shouldEqual StatusCodes.Forbidden
        }
      }
    }
  }

  it should "return forbidden if missing required session" in {
    Get(s"/game/$game1UUIDPickedAtRandom") ~> routes ~> check {
      status shouldEqual StatusCodes.Forbidden
    }
  }

  private def extractSession(httpHeader: HttpHeader): Option[UserSession] =
    oneOff.clientSessionManager.decode(httpHeader.value()).toOption

  private def createGameAndGetValidSession(body: RequestTransformer => Unit) =
    Post("/game") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
      val sessionHeader = header(headerName)
      sessionHeader.isDefined shouldEqual true
      body(addHeader(sessionHeader.get))
    }
}
