package org.zarucki.rest

import java.util.UUID

import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Sink
import com.softwaremill.session.{HeaderConfig, SessionConfig, SessionManager}
import com.typesafe.scalalogging.StrictLogging
import com.softwaremill.session.SessionOptions._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.scalatest.BeforeAndAfterEach
import org.zarucki.game.battleship.BattleshipGame
import org.zarucki._
import org.zarucki.game.{GameServerLookup, InMemoryGameServerLookup}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

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
  var testBattleshipGame: BattleshipGame = _

  val testGameServerLookup = new InMemoryGameServerLookup[TwoPlayersGameServer[BattleshipGame]] {
    override def startNewGameServer(newGame: TwoPlayersGameServer[BattleshipGame]): UniqueId = {
      val newGameId = gameIdsToGive.head
      gameIdsToGive = gameIdsToGive.tail
      concurrentStorage.put(newGameId, newGame)
      newGameId
    }
  }

  override protected def beforeEach(): Unit = {
    gameIdsToGive = List(game1UUIDPickedAtRandom, game2UUIDPickedAtRandom, UUID.randomUUID())
    userIdsToGive = List(player1UUIDPickedAtRandom, player2UUIDPickedAtRandom, UUID.randomUUID())
    testBattleshipGame = new BattleshipGame(10, 10)
  }

  override protected def afterEach(): Unit =
    testGameServerLookup.clear()

  implicit val testSessionManager =
    new SessionManager[UserSession](
      SessionConfig
        .defaultConfig(testSecret64characterLong)
        .copy(sessionHeaderConfig = HeaderConfig(headerName, headerName))
    )

  val routes = Route.seal(new GameRouting[TwoPlayersGameServer[BattleshipGame], BattleshipGame] with StrictLogging {
    override implicit val sessionManager: SessionManager[UserSession] = testSessionManager
    override implicit val executor: ExecutionContext = testExecutor
    override implicit val sessionCreator: SessionCreator = new SessionCreator {
      override def newSession(): UserSession = {
        val newUserId = userIdsToGive.head
        userIdsToGive = userIdsToGive.tail
        UserSession(userId = newUserId)
      }
    }
    override val gameServerLookup: GameServerLookup[UniqueId, TwoPlayersGameServer[BattleshipGame]] =
      testGameServerLookup
    override def newGameServerForPlayer(
        userId: UniqueId
    ): TwoPlayersGameServer[BattleshipGame] = {
      new TwoPlayersGameServer(hostPlayerId = userId, game = testBattleshipGame)
    }
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

      testGameServerLookup
        .getGameServerById(game1UUIDPickedAtRandom)
        .value shouldEqual TwoPlayersGameServer[BattleshipGame](
        hostPlayerId = player1UUIDPickedAtRandom,
        game = testBattleshipGame,
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
        responseAs[GameError] shouldEqual GameErrors.alreadyJoinedGame
      }
    }
  }

  it should "return already in game for a player that joined game and tries to join again" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual s"game state $game1UUIDPickedAtRandom"
        header(headerName).isDefined shouldEqual true

        Post(s"/game/$game1UUIDPickedAtRandom/join") ~> addHeader(header(headerName).get) ~> routes ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[GameError] shouldEqual GameErrors.alreadyJoinedGame
        }
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
          responseAs[GameError] shouldEqual GameErrors.gameFull
        }
      }
    }
  }

  it should "return game state with correct session" in {
    createGameAndGetValidSession { addSessionTransform =>
      Get(s"/game/$game1UUIDPickedAtRandom") ~> addSessionTransform ~> routes ~> check {

        status shouldEqual StatusCodes.OK

        responseAs[TurnedBasedGameStatus] shouldEqual TurnedBasedGameStatus(gameStatus = AwaitingPlayers)

        val completionStage = responseEntity
          .getDataBytes()
          .map(_.utf8String)
          .runWith(Sink.reduce[String](_ + _), materializer)

        Await.result(completionStage, Duration.Inf) shouldEqual "{\"gameStatus\":\"AWAITING_PLAYERS\"}"
      }
    }
  }

  it should "return YourTurn if both players are in the game for the invited player " in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        Get(s"/game/$game1UUIDPickedAtRandom") ~> addHeader(header(headerName).get) ~> routes ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[TurnedBasedGameStatus] shouldEqual TurnedBasedGameStatus(gameStatus = YourTurn)
        }
      }
    }
  }

  it should "return WaitingForOpponentMove if both players are in the game for the invited player " in {
    createGameAndGetValidSession { addSessionTransform =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        Get(s"/game/$game1UUIDPickedAtRandom") ~> addSessionTransform ~> routes ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[TurnedBasedGameStatus] shouldEqual TurnedBasedGameStatus(gameStatus = WaitingForOpponentMove)
        }
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
