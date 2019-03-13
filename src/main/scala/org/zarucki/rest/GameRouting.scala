package org.zarucki.rest

import akka.http.scaladsl.model.StatusCodes
import com.softwaremill.session.SessionDirectives
import com.typesafe.scalalogging.Logger
import org.zarucki.rest.GameSession.UniqueId
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Directive1, Route}
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._

import scala.concurrent.ExecutionContext

trait GameRouting extends SessionSupport[GameSession] {
  protected val logger: Logger

  implicit def executor: ExecutionContext
  implicit def sessionCreator: SessionCreator

  // TODO: rejection should be wrapped and returned as json
  val routes: Route = {
    pathPrefix("game") {
      pathEndOrSingleSlash {
        post {
          // create the game, send credentials
          logger.info(s"POST /game")
          val session: GameSession = sessionCreator.newSession()
          logger.info(s"New session $session")
          // TODO: return json link
          // TODO: save somewhere that given session takes players in given game
          setGameSession(session) {
            complete("new game")
          }
        }
      } ~
        pathPrefix(JavaUUID) { gameUUID =>
          path("join") {
            post {
              // join existing game, send credentials, and initial state
              logger.info(s"POST /game/$gameUUID/join")
              val otherPlayerSession = sessionCreator.newSessionForGame(gameUUID)

              // TODO: if player who already joined will try to join again, treat it as simple getState
              // TODO: check if game server already full?
              setGameSession(otherPlayerSession) {
                complete("game state")
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
