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

trait GameRouting extends SessionSupport[GameSession] {
  protected val logger: Logger

  implicit def executor: ExecutionContext
  implicit def sessionCreator: SessionCreator
  def gameStateStore: GameStateStore[TwoPlayersGameState]

  // TODO: rejection should be wrapped and returned as json
  val routes: Route = {
    pathPrefix("game") {
      pathEndOrSingleSlash {
        post {
          val session: GameSession = sessionCreator.newSession()

          // TODO: check if we overwritten something? what to do then?
          gameStateStore.saveNewGame(session.gameId, new TwoPlayersGameState(hostPlayerId = session.playerId))

          extractUri { uri =>
            setGameSession(session) {
              complete(GameInvitation(uri.withPath(Path(s"/game/${session.gameId}/join")).toString()))
            }
          }
        }
      } ~
        pathPrefix(JavaUUID) { gameUUID =>
          path("join") {
            post {
              // TODO: if someone comes with session, treat as noop or game state?
              // TODO: if player who already joined will try to join again, treat it as simple getState
              // join existing game, send credentials, and initial state
              logger.info(s"POST /game/$gameUUID/join")

              gameStateStore.getGameById(gameUUID) match {
                case Some(gameState) if gameState.isTherePlaceForAnotherPlayer =>
                  // TODO: it is weird that session generates gameid and userids
                  val otherPlayerSession = sessionCreator.newSessionForGame(gameUUID)

                  gameStateStore.updateGameState(
                    gameUUID,
                    state => state.copy(otherPlayerId = Some(otherPlayerSession.playerId))
                  )

                  setGameSession(otherPlayerSession) {
                    complete("game state")
                  }
                case Some(_) => complete(StatusCodes.Forbidden -> "Game already full.")
                case _       => complete(StatusCodes.NotFound)
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

  protected def setGameSession(session: GameSession): Directive0 =
    SessionDirectives.setSession(oneOff, usingHeaders, session)

  protected def requiredSessionForGame(gameId: UniqueId): Directive1[GameSession] =
    requiredSession(oneOff, usingHeaders).flatMap { session =>
      if (session.gameId == gameId) {
        provide(session)
      } else {
        reject(oneOff.clientSessionManager.sessionMissingRejection)
      }
    }
}
