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
import io.circe.generic.auto._
import org.zarucki.game.GameServerLookup
import org.zarucki.{AwaitingPlayers, GameStatus, UniqueId}

import scala.concurrent.ExecutionContext

trait GameRouting extends SessionSupport[UserSession] {
  protected val logger: Logger

  implicit def executor: ExecutionContext
  implicit def sessionCreator: SessionCreator
  def gameServerLookup: GameServerLookup[UniqueId, TwoPlayersGameServer]

  // TODO: rejection should be wrapped and returned as json
  val routes: Route = {
    pathPrefix("game") {
      pathEndOrSingleSlash {
        post {
          val session: UserSession = sessionCreator.newSession()

          // TODO: what if someone comes with already valid session
          // TODO: should one person be able to start multiple games at once?
          // TODO: check if we overwritten something? what to do then?
          val newGameId = gameServerLookup.startNewGameServer(new TwoPlayersGameServer(hostPlayerId = session.userId))

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

                        gameServerLookup.updateGameServer(
                          gameUUID,
                          _.copy(otherPlayerId = Some(otherPlayerSession.userId))
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
              put {
                logger.info(s"PUT /game/$gameUUID")

                requireSessionAndCheckIfPlayerIsPartOfGame(gameUUID) { (session, gameServer) =>
                  complete(StatusCodes.OK)
                }
              } ~
                get {
                  logger.info(s"GET /game/$gameUUID")

                  requireSessionAndCheckIfPlayerIsPartOfGame(gameUUID) { (session, gameServer) =>
                    logger.info("got session: " + session)
                    logger.info("got game state: " + gameServer)
                    if (gameServer.isTherePlaceForAnotherPlayer) {
                      complete(new TurnedBasedGameStatus(gameStatus = AwaitingPlayers))
                    } else {
                      complete("game state")
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
  ): Directive[(UserSession, TwoPlayersGameServer)] =
    requiredSession(oneOff, usingHeaders).flatMap { session =>
      gameServerLookup.getGameServerById(gameId) match {
        case Some(gameServer) if gameServer.playerIdSet(session.userId) => tprovide((session, gameServer))
        case Some(_)                                                    => reject(AuthorizationFailedRejection)
        case None                                                       => reject()
      }
    }
}

case class GameInvitation(invitationLink: String)

object GameErrors {
  val gameFull = GameError("Game already full.")
  val alreadyJoinedGame = GameError("Already joined game.")
}

case class GameError(message: String)
case class TurnedBasedGameStatus(gameStatus: GameStatus)

// TODO: generalize this to N players
case class TwoPlayersGameServer(hostPlayerId: UniqueId, otherPlayerId: Option[UniqueId] = None) {
  lazy val playerIdSet = Set(hostPlayerId) ++ otherPlayerId.toSet
  def isTherePlaceForAnotherPlayer: Boolean = otherPlayerId.isEmpty
}
