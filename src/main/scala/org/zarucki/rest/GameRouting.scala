package org.zarucki.rest

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.Logger
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._

import scala.concurrent.ExecutionContext

trait GameRouting extends SessionSupport[Session] {
  protected val logger: Logger

  implicit def executor: ExecutionContext
  implicit def sessionCreator: SessionCreator

  val routes: Route = {
    pathPrefix("game") {
      pathEndOrSingleSlash {
        post {
          // create the game, send credentials
          logger.info(s"POST /game")
          val session: Session = sessionCreator.newSession()
          logger.info(s"New session $session")
          // TODO: get id of the game, and return link
          // TODO: save somewhere that given session takes players in given game
          setSession(oneOff, usingHeaders, session) {
            complete("new game")
          }
        }
      } ~
        pathPrefix(JavaUUID) { gameUuid =>
          path("join") {
            post {
              // join existing game, send credentials, and initial state
              logger.info(s"POST /game/$gameUuid/join")
              complete(StatusCodes.OK)
            }
          } ~
            pathEndOrSingleSlash {
              put {
                logger.info(s"PUT /game/$gameUuid")
                complete(StatusCodes.OK)

              } ~
                get {
                  logger.info(s"GET /game/$gameUuid")

                  requiredSession(oneOff, usingHeaders) { session =>
                    if (session.gameId == gameUuid) {
                      logger.info("got session: " + session)
                      complete("game state")
                    } else {
                      complete(StatusCodes.Forbidden -> "Game id not matching session.")
                    }
                  }
                }
            }
        }
    }
  }
}
