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
          val session: Session = sessionCreator.getNewSession()
          logger.info(s"New session $session")
          // TODO: get id of the game, and return link
          // TODO: save somewhere that given session takes playes in given game
          setSession(oneOff, usingHeaders, session) {
            complete("new game")
          }
        }
      } ~
        pathPrefix(LongNumber) { gameId =>
          path("join") {
            post {
              // join existing game, send credentials, and initial state
              logger.info(s"POST /game/$gameId/join")
              complete(StatusCodes.OK)
            }
          } ~
            pathEndOrSingleSlash {
              put {
                logger.info(s"PUT /game/$gameId")
                complete(StatusCodes.OK)

              } ~
                get {
                  logger.info(s"GET /game/$gameId")

                  session[Session](oneOff, usingHeaders) { sessionResult =>
                    sessionResult.toOption match {
                      case Some(session) =>
                        logger.info("got userid: " + session.userId)
                        complete("game state")
                      case _ =>
                        logger.warn("Missing required session!")
                        complete(StatusCodes.Forbidden -> "Missing session")
                    }
                  }
                }
            }
        }
    }
  }
}
