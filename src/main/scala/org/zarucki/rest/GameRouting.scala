package org.zarucki.rest

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext

trait GameRouting extends BattleshipSessionSupport {
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
          val session: BattleshipSession = sessionCreator.newSession()
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

              setGameSession(otherPlayerSession) {
                complete(StatusCodes.OK)
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
}
