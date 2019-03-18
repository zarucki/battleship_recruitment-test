package org.zarucki.rest

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.softwaremill.session.SessionDirectives
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.typesafe.scalalogging.Logger
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.auto._
import org.zarucki.{AwaitingPlayers, GameStatus, UniqueId}
import org.zarucki.game.GameStateStore

import scala.concurrent.ExecutionContext

trait GameRouting extends SessionSupport[UserSession] {
  protected val logger: Logger

  implicit def executor: ExecutionContext
  implicit def sessionCreator: SessionCreator
  def gameStateStore: GameStateStore[UniqueId, TwoPlayersGameState]

  // TODO: rejection should be wrapped and returned as json
  val routes: Route = {
    pathPrefix("game") {
      pathEndOrSingleSlash {
        post {
          val session: UserSession = sessionCreator.newSession()

          // TODO: what if someone comes with already valid session
          // TODO: should one person be able to start multiple games at once?
          // TODO: check if we overwritten something? what to do then?
          val newGameId = gameStateStore.saveNewGame(new TwoPlayersGameState(hostPlayerId = session.userId))

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

              gameStateStore.getGameById(gameUUID) match {
                case None => complete(StatusCodes.NotFound)
                case Some(gameState) =>
                  optionalSession(oneOff, usingHeaders) {
                    case Some(existingSession) if gameState.playerIdSet(existingSession.userId) =>
                      complete(StatusCodes.OK -> GameErrors.alreadyJoinedGame)
                    case existingSession =>
                      if (!gameState.isTherePlaceForAnotherPlayer) {
                        complete(StatusCodes.Forbidden -> GameErrors.gameFull)
                      } else {
                        val otherPlayerSession = existingSession.getOrElse(sessionCreator.newSession())

                        gameStateStore.updateGameState(
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

                requireSessionAndCheckIfPlayerIsPartOfGame(gameUUID) { (session, gameState) =>
                  complete(StatusCodes.OK)
                }
              } ~
                get {
                  logger.info(s"GET /game/$gameUUID")

                  requireSessionAndCheckIfPlayerIsPartOfGame(gameUUID) { (session, gameState) =>
                    logger.info("got session: " + session)
                    logger.info("got game state: " + gameState)
                    if (gameState.isTherePlaceForAnotherPlayer) {
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
  ): Directive[(UserSession, TwoPlayersGameState)] =
    requiredSession(oneOff, usingHeaders).flatMap { session =>
      gameStateStore.getGameById(gameId) match {
        case Some(gameState) if gameState.playerIdSet(session.userId) => tprovide((session, gameState))
        case Some(_)                                                  => reject(AuthorizationFailedRejection)
        case None                                                     => reject()
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

case class TwoPlayersGameState(hostPlayerId: UniqueId, otherPlayerId: Option[UniqueId] = None) {
  lazy val playerIdSet = Set(hostPlayerId) ++ otherPlayerId.toSet
  def isTherePlaceForAnotherPlayer: Boolean = otherPlayerId.isEmpty
}
