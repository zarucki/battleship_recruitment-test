package org.zarucki.rest

import java.util.UUID

import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Sink
import cats.effect.IO
import com.softwaremill.session.{HeaderConfig, SessionConfig, SessionManager}
import com.typesafe.scalalogging.StrictLogging
import com.softwaremill.session.SessionOptions._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.BeforeAndAfterEach
import org.zarucki.game.battleship._
import org.zarucki._
import org.zarucki.game.{GameServerLookup, InMemoryGameServerLookup}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class BattleshipGameRoutingSpec extends BaseRouteSpec with BeforeAndAfterEach {
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
  var testBattleshipRestGame: BattleshipTurnedBasedRestGame = _

  val testGameServerLookup = new InMemoryGameServerLookup[TwoPlayersGameServer[BattleshipTurnedBasedRestGame]] {
    override def startNewGameServer(newGame: TwoPlayersGameServer[BattleshipTurnedBasedRestGame]): IO[UniqueId] = {
      IO {
        val newGameId = gameIdsToGive.head
        gameIdsToGive = gameIdsToGive.tail
        concurrentStorage.put(newGameId, newGame)
        newGameId
      }
    }
  }

  override protected def beforeEach(): Unit = {
    gameIdsToGive = List(game1UUIDPickedAtRandom, game2UUIDPickedAtRandom, UUID.randomUUID())
    userIdsToGive = List(player1UUIDPickedAtRandom, player2UUIDPickedAtRandom, UUID.randomUUID())
    testBattleshipGame = new BattleshipGame(10, 10)
    testBattleshipRestGame = new BattleshipTurnedBasedRestGame(testBattleshipGame)
    testBattleshipGame.placeShip(0, ShipLocation(North, BoardAddress(x = 0, y = 0)), OneLinerShip.fourDecker)
    testBattleshipGame.placeShip(1, ShipLocation(North, BoardAddress(x = 1, y = 0)), OneLinerShip.fourDecker)
    testBattleshipGame.startGame()
  }

  override protected def afterEach(): Unit = {
    testGameServerLookup.clear().unsafeRunSync()
  }

  implicit val testSessionManager =
    new SessionManager[UserSession](
      SessionConfig
        .defaultConfig(testSecret64characterLong)
        .copy(sessionHeaderConfig = HeaderConfig(headerName, headerName))
    )

  val routes = Route.seal(
    new GameRouting[
      TwoPlayersGameServer[BattleshipTurnedBasedRestGame],
      BattleshipTurnedBasedRestGame,
      HitCommand,
      HitReport
    ] with HitCommandEncoding with StrictLogging {
      override implicit val sessionManager: SessionManager[UserSession] = testSessionManager
      override implicit val executor: ExecutionContext = testExecutor
      override implicit val sessionCreator: SessionCreator = new SessionCreator {
        override def newSession(): UserSession = {
          val newUserId = userIdsToGive.head
          userIdsToGive = userIdsToGive.tail
          UserSession(userId = newUserId)
        }
      }

      override val gameServerLookup
        : GameServerLookup[IO, UniqueId, TwoPlayersGameServer[BattleshipTurnedBasedRestGame]] = {
        testGameServerLookup
      }

      override def newGameServerForPlayer(
          userId: UniqueId
      ): TwoPlayersGameServer[BattleshipTurnedBasedRestGame] = {
        new TwoPlayersGameServer(hostPlayerId = userId, game = testBattleshipRestGame)
      }
    }.routes
  )

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
        .unsafeRunSync()
        .value shouldEqual TwoPlayersGameServer[BattleshipTurnedBasedRestGame](
        hostPlayerId = player1UUIDPickedAtRandom,
        game = testBattleshipRestGame,
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
        status shouldEqual StatusCodes.BadRequest
        responseAs[GameError] shouldEqual RestGameErrors.alreadyJoinedGame.gameError
      }
    }
  }

  it should "return already in game for a player that joined game and tries to join again" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[TurnedBasedGameStatus] shouldEqual TurnedBasedGameStatus(YourTurn)
        header(headerName).isDefined shouldEqual true

        Post(s"/game/$game1UUIDPickedAtRandom/join") ~> addHeader(header(headerName).get) ~> routes ~> check {
          status shouldEqual StatusCodes.BadRequest
          responseAs[GameError] shouldEqual RestGameErrors.alreadyJoinedGame.gameError
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
          responseAs[TurnedBasedGameStatus] shouldEqual TurnedBasedGameStatus(YourTurn)
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
        responseAs[TurnedBasedGameStatus] shouldEqual TurnedBasedGameStatus(YourTurn)
      }
    }
  }

  it should "return forbidden if game already full" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
          status shouldEqual StatusCodes.Forbidden
          responseAs[GameError] shouldEqual RestGameErrors.gameFull.gameError
        }
      }
    }
  }

  it should "return forbidden if game already full as string" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
          status shouldEqual StatusCodes.Forbidden
          responseEntityAsString(responseEntity) shouldEqual """{"message":"Game already full."}"""
        }
      }
    }
  }

  it should "return game state with correct session" in {
    createGameAndGetValidSession { addSessionTransform =>
      Get(s"/game/$game1UUIDPickedAtRandom") ~> addSessionTransform ~> routes ~> check {

        status shouldEqual StatusCodes.OK

        responseAs[TurnedBasedGameStatus] shouldEqual TurnedBasedGameStatus(gameStatus = AwaitingPlayers)

        responseEntityAsString(responseEntity) shouldEqual """{"gameStatus":"AWAITING_PLAYERS","yourScore":0,"opponentScore":0}"""
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

  it should "return forbidden when trying to access non existing game" in {
    createGameAndGetValidSession { addSessionTransform =>
      Get(s"/game/${UUID.randomUUID()}") ~> addSessionTransform ~> routes ~> check {
        status shouldEqual StatusCodes.Forbidden
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

  it should "return Hit if player shoots right" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        Put(
          s"/game/$game1UUIDPickedAtRandom",
          HttpEntity(ContentTypes.`application/json`, HitCommand("A1").asJson.toString())
        ) ~>
          addHeader(header(headerName).get) ~> routes ~> check {
          status shouldEqual StatusCodes.OK

          responseAs[HitReport] shouldEqual Hit("FOUR_DECKER", sunken = false)
        }
      }
    }
  }

  it should "return Hit if player shoots right checking string" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        Put(
          s"/game/$game1UUIDPickedAtRandom",
          HttpEntity(ContentTypes.`application/json`, HitCommand("A1").asJson.toString())
        ) ~>
          addHeader(header(headerName).get) ~> routes ~> check {
          status shouldEqual StatusCodes.OK

          responseEntityAsString(responseEntity) shouldEqual """{"shipType":"FOUR_DECKER","sunken":false,"result":"HIT"}"""
        }
      }
    }
  }

  it should "return Miss if hits the same spot again" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        val player2SessionHeader = header(headerName).get
        Put(
          s"/game/$game1UUIDPickedAtRandom",
          HttpEntity(ContentTypes.`application/json`, HitCommand("A1").asJson.toString())
        ) ~> addHeader(player2SessionHeader) ~> routes ~> check {
          Put(
            s"/game/$game1UUIDPickedAtRandom",
            HttpEntity(ContentTypes.`application/json`, HitCommand("A1").asJson.toString())
          ) ~> addHeader(player2SessionHeader) ~> routes ~> check {
            status shouldEqual StatusCodes.OK
            responseAs[HitReport] shouldEqual Miss
          }
        }
      }
    }
  }

  it should "return sunken hit report if whole ship was destroyed" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        val player2SessionHeader = header(headerName).get
        Put(
          s"/game/$game1UUIDPickedAtRandom",
          HttpEntity(ContentTypes.`application/json`, HitCommand("A1").asJson.toString())
        ) ~> addHeader(player2SessionHeader) ~> routes ~> check {
          Put(
            s"/game/$game1UUIDPickedAtRandom",
            HttpEntity(ContentTypes.`application/json`, HitCommand("B1").asJson.toString())
          ) ~> addHeader(player2SessionHeader) ~> routes ~> check {
            Put(
              s"/game/$game1UUIDPickedAtRandom",
              HttpEntity(ContentTypes.`application/json`, HitCommand("C1").asJson.toString())
            ) ~> addHeader(player2SessionHeader) ~> routes ~> check {
              Put(
                s"/game/$game1UUIDPickedAtRandom",
                HttpEntity(ContentTypes.`application/json`, HitCommand("D1").asJson.toString())
              ) ~> addHeader(player2SessionHeader) ~> routes ~> check {
                status shouldEqual StatusCodes.OK
                responseAs[HitReport] shouldEqual Hit("FOUR_DECKER", sunken = true)
              }
            }
          }
        }
      }
    }
  }

  it should "return Miss if player shoots wrong" in {
    createGameAndGetValidSession { firstGameSessionTransform =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        Put(
          s"/game/$game1UUIDPickedAtRandom",
          HttpEntity(ContentTypes.`application/json`, HitCommand("A2").asJson.toString())
        ) ~>
          addHeader(header(headerName).get) ~> routes ~> check {
          status shouldEqual StatusCodes.OK

          responseAs[HitReport] shouldEqual Miss
        }
      }
    }
  }

  it should "return Miss if player shoots wrong as string" in {
    createGameAndGetValidSession { firstGameSessionTransform =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        Put(
          s"/game/$game1UUIDPickedAtRandom",
          HttpEntity(ContentTypes.`application/json`, HitCommand("A2").asJson.toString())
        ) ~>
          addHeader(header(headerName).get) ~> routes ~> check {
          status shouldEqual StatusCodes.OK

          responseEntityAsString(responseEntity) shouldEqual """{"result":"MISS"}"""
        }
      }
    }
  }

  it should "return error that it is not player turn if first player makes first move" in {
    createGameAndGetValidSession { firstPlayerSessionTransform =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        Put(
          s"/game/$game1UUIDPickedAtRandom",
          HttpEntity(ContentTypes.`application/json`, HitCommand("A1").asJson.toString())
        ) ~> firstPlayerSessionTransform ~> routes ~> check {
          status shouldEqual StatusCodes.BadRequest

          responseAs[GameError] shouldEqual GameError("Turn belongs to other player.")
        }
      }
    }
  }

  it should "still be player turn if hits correctly" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        val player2SessionHeader = header(headerName).get
        Put(
          s"/game/$game1UUIDPickedAtRandom",
          HttpEntity(ContentTypes.`application/json`, HitCommand("A1").asJson.toString())
        ) ~> addHeader(player2SessionHeader) ~> routes ~> check {
          Get(s"/game/$game1UUIDPickedAtRandom") ~> addHeader(player2SessionHeader) ~> routes ~> check {
            status shouldEqual StatusCodes.OK
            responseAs[TurnedBasedGameStatus] shouldEqual TurnedBasedGameStatus(gameStatus = YourTurn, yourScore = 1)
          }
        }
      }
    }
  }

  it should "be turn of other player when player missed" in {
    createGameAndGetValidSession { _ =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        val player2SessionHeader = header(headerName).get
        Put(
          s"/game/$game1UUIDPickedAtRandom",
          HttpEntity(ContentTypes.`application/json`, HitCommand("A2").asJson.toString())
        ) ~> addHeader(player2SessionHeader) ~> routes ~> check {
          Get(s"/game/$game1UUIDPickedAtRandom") ~> addHeader(player2SessionHeader) ~> routes ~> check {
            status shouldEqual StatusCodes.OK
            responseAs[TurnedBasedGameStatus] shouldEqual TurnedBasedGameStatus(gameStatus = WaitingForOpponentMove)
          }
        }
      }
    }
  }

  it should "work properly for example game" in {
    createGameAndGetValidSession { firstPlayerHeaderTransform =>
      Post(s"/game/$game1UUIDPickedAtRandom/join") ~> routes ~> check {
        val secondPlayerHeaderTransform = addHeader(header(headerName).get)

        shootField(game1UUIDPickedAtRandom, "A1")(secondPlayerHeaderTransform) {
          responseAs[HitReport] shouldEqual Hit("FOUR_DECKER", sunken = false)
          shootField(game1UUIDPickedAtRandom, "A2")(secondPlayerHeaderTransform) {
            responseAs[HitReport] shouldEqual Miss
            shootField(game1UUIDPickedAtRandom, "B1")(secondPlayerHeaderTransform) {
              status shouldEqual StatusCodes.BadRequest
              responseAs[GameError] shouldEqual GameError("Turn belongs to other player.")

              getGameStatus(game1UUIDPickedAtRandom)(firstPlayerHeaderTransform) {
                entityAs[TurnedBasedGameStatus] shouldEqual TurnedBasedGameStatus(
                  YourTurn,
                  yourScore = 0,
                  opponentScore = 1
                )

                shootField(game1UUIDPickedAtRandom, "A2")(firstPlayerHeaderTransform) {
                  shootField(game1UUIDPickedAtRandom, "B2")(firstPlayerHeaderTransform) {
                    shootField(game1UUIDPickedAtRandom, "C2")(firstPlayerHeaderTransform) {
                      shootField(game1UUIDPickedAtRandom, "D2")(firstPlayerHeaderTransform) {
                        responseAs[HitReport] shouldEqual Hit("FOUR_DECKER", sunken = true)

                        shootField(game1UUIDPickedAtRandom, "C7")(firstPlayerHeaderTransform) {
                          status shouldEqual StatusCodes.BadRequest
                          responseAs[GameError] shouldEqual GameError("Cannot shoot after game has finished.")

                          getGameStatus(game1UUIDPickedAtRandom)(firstPlayerHeaderTransform) {
                            entityAs[TurnedBasedGameStatus] shouldEqual TurnedBasedGameStatus(
                              YouWon,
                              yourScore = 4,
                              opponentScore = 1
                            )

                            getGameStatus(game1UUIDPickedAtRandom)(secondPlayerHeaderTransform) {
                              entityAs[TurnedBasedGameStatus] shouldEqual TurnedBasedGameStatus(
                                YouLost,
                                yourScore = 1,
                                opponentScore = 4
                              )
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private def getGameStatus[T](gameId: UUID)(requestTransformer: RequestTransformer)(body: => T): T = {
    Get(s"/game/$gameId") ~> requestTransformer ~> routes ~> check {
      body
    }
  }

  private def shootField[T](gameId: UUID, position: String)(requestTransformer: RequestTransformer)(body: => T): T = {
    Put(
      s"/game/$gameId",
      HttpEntity(ContentTypes.`application/json`, HitCommand(position).asJson.toString())
    ) ~> requestTransformer ~> routes ~> check {
      body
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

  private def responseEntityAsString(responseEntity: HttpEntity): String = {
    val completionStage = responseEntity
      .getDataBytes()
      .map(_.utf8String)
      .runWith(Sink.reduce[String](_ + _), materializer)

    Await.result(completionStage, Duration.Inf)
  }
}
