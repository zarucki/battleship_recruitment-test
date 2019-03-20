package org.zarucki.rest

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.softwaremill.session.SessionDirectives
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.typesafe.scalalogging.Logger
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import org.zarucki.game.GameServerLookup
import org.zarucki._

import scala.concurrent.ExecutionContext

trait GameRouting[TGameServer <: MultiPlayerGameServer[TGameServer, TGame], TGame <: RestGame[TCommand, TCommandResult], TCommand, TCommandResult]
    extends SessionSupport[UserSession] {
  protected val logger: Logger

  implicit def commandEncoder: Decoder[TCommand]
  implicit def commandResultDecoder: Encoder[TCommandResult]
  implicit def executor: ExecutionContext
  implicit def sessionCreator: SessionCreator

  def gameServerLookup: GameServerLookup[UniqueId, TGameServer]
  def newGameServerForPlayer(userId: UniqueId): TGameServer

  // TODO: rejection should be wrapped and returned as json
  val routes: Route = {
    pathPrefix("game") {
      pathEndOrSingleSlash {
        post {
          val session = sessionCreator.newSession()

          // TODO: what if someone comes with already valid session
          // TODO: should one person be able to start multiple games at once?
          // TODO: check if we overwritten something? what to do then?
          val newGameId = gameServerLookup.startNewGameServer(newGameServerForPlayer(session.userId))

          extractUri { uri =>
            setGameSession(session) {
              complete(GameInvitation(uri.withPath(Path(s"/game/$newGameId/join")).toString()))
            }
          }
        }
      } ~
        pathPrefix(JavaUUID) { gameUUID =>
          path("join") {
            post {
              logger.info(s"POST /game/$gameUUID/join")

              gameServerLookup.getGameServerById(gameUUID) match {
                case None => complete(StatusCodes.NotFound)
                case Some(gameServer) =>
                  optionalSession(oneOff, usingHeaders) {
                    case Some(existingSession) if gameServer.playerIdSet(existingSession.userId) =>
                      complete(StatusCodes.OK -> GameErrors.alreadyJoinedGame)
                    case existingSession =>
                      if (!gameServer.isTherePlaceForAnotherPlayer) {
                        complete(StatusCodes.Forbidden -> GameErrors.gameFull)
                      } else {
                        val otherPlayerSession = existingSession.getOrElse(sessionCreator.newSession())

                        // TODO: If game server is for N players it does not mean yet that the game can start
                        gameServerLookup.updateGameServer(
                          gameUUID,
                          _.joinPlayer(otherPlayerSession.userId)
                        )

                        val result = complete(s"game state $gameUUID")
                        if (existingSession.isDefined) {
                          result
                        } else {
                          setGameSession(otherPlayerSession) {
                            result
                          }
                        }
                      }
                  }
              }
            }
          } ~
            pathEndOrSingleSlash {
              // TODO: when game finished returned that game is done
              put {
                entity(as[TCommand]) { command =>
                  logger.info(s"PUT /game/$gameUUID")

                  requireSessionAndCheckIfPlayerIsPartOfGame(gameUUID) { (session, gameServer, playerNumber) =>
                    complete(gameServer.getGame().issueCommand(playerNumber, command))
                  }
                }
              } ~
                get {
                  logger.info(s"GET /game/$gameUUID")

                  requireSessionAndCheckIfPlayerIsPartOfGame(gameUUID) { (session, gameServer, playerNumber) =>
                    logger.info("got session: " + session)
                    logger.info("got game state: " + gameServer)
                    if (gameServer.isTherePlaceForAnotherPlayer) {
                      complete(new TurnedBasedGameStatus(gameStatus = AwaitingPlayers))
                    } else {
                      complete(gameServer.getGame().getStatus(playerNumber))
                    }
                  }
                }
            }
        }
    }
  }

  protected def setGameSession(session: UserSession): Directive0 =
    SessionDirectives.setSession(oneOff, usingHeaders, session)

  protected def requireSessionAndCheckIfPlayerIsPartOfGame(
      gameId: UniqueId
  ): Directive[(UserSession, TGameServer, Int)] =
    requiredSession(oneOff, usingHeaders).flatMap { session =>
      gameServerLookup.getGameServerById(gameId) match {
        case Some(gameServer) if gameServer.playerIdSet(session.userId) =>
          tprovide((session, gameServer, gameServer.getPlayerNumber(session.userId)))
        case Some(_) => reject(AuthorizationFailedRejection)
        case None    => reject()
      }
    }
}

case class GameInvitation(invitationLink: String)

object GameErrors {
  val gameFull = GameError("Game already full.")
  val alreadyJoinedGame = GameError("Already joined game.")
}

case class GameError(message: String)

// TODO: generalize this to N players?
case class TwoPlayersGameServer[Game](hostPlayerId: UniqueId, game: Game, otherPlayerId: Option[UniqueId] = None)
    extends MultiPlayerGameServer[TwoPlayersGameServer[Game], Game] {

  lazy val playerIdSet = Set(hostPlayerId) ++ otherPlayerId.toSet

  def isTherePlaceForAnotherPlayer: Boolean = otherPlayerId.isEmpty

  override def joinPlayer(playerId: UniqueId): TwoPlayersGameServer[Game] = {
    copy(otherPlayerId = Some(playerId))
  }
  override def getGame(): Game = game

  override def getPlayerNumber(playerId: UniqueId): Int = {
    if (playerId == hostPlayerId) {
      0
    } else {
      1
    }
  }
}

trait MultiPlayerGameServer[GameServer <: MultiPlayerGameServer[GameServer, Game], Game] {
  def playerIdSet: Set[UniqueId]
  def isTherePlaceForAnotherPlayer: Boolean
  def joinPlayer(playerId: UniqueId): GameServer
  def getPlayerNumber(playerId: UniqueId): Int
  def getGame(): Game
}
