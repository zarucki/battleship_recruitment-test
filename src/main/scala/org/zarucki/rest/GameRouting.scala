package org.zarucki.rest

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri.Path
import com.softwaremill.session.SessionDirectives
import com.typesafe.scalalogging.Logger
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Directive1, Route}
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.zarucki.UniqueId
import org.zarucki.game.GameStateStore

import scala.concurrent.ExecutionContext

case class GameInvitation(invitationLink: String)
case class TwoPlayersGameState(hostPlayerId: UniqueId, otherPlayerId: Option[UniqueId] = None) {
  lazy val playerIdSet = Set(hostPlayerId) ++ otherPlayerId.toSet
  def isTherePlaceForAnotherPlayer: Boolean = otherPlayerId.isEmpty
}

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
                      complete(StatusCodes.OK -> "Already in game.")
                    case optSession =>
                      if (!gameState.isTherePlaceForAnotherPlayer) {
                        complete(StatusCodes.Forbidden -> "Game already full.")
                      } else {
                        val otherPlayerSession = optSession.getOrElse(sessionCreator.newSession())

                        gameStateStore.updateGameState(
                          gameUUID,
                          _.copy(otherPlayerId = Some(otherPlayerSession.userId))
                        )

                        val result = complete("game state")
                        if (optSession.isDefined) {
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

                requiredSessionForGame(gameUUID) { session =>
                  complete(StatusCodes.OK)
                }
              } ~
                get {
                  logger.info(s"GET /game/$gameUUID")

                  requiredSessionForGame(gameUUID) { session =>
                    logger.info("got session: " + session)
                    complete("game state")
                  }
                }
            }
        }
    }
  }

  protected def setGameSession(session: UserSession): Directive0 =
    SessionDirectives.setSession(oneOff, usingHeaders, session)

  protected def requiredSessionForGame(gameId: UniqueId): Directive1[UserSession] =
    requiredSession(oneOff, usingHeaders).flatMap { session =>
      provide(session)
    }
}
