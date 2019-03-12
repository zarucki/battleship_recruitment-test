package org.zarucki.rest

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.Logger

trait GameRouting {
  protected val logger: Logger

  val routes: Route = {
    pathPrefix("game") {
      pathEndOrSingleSlash {
        post {
          // create the game, send credentials
          logger.info(s"POST /game")
          complete(StatusCodes.OK)
        }
      } ~
        pathPrefix(LongNumber) { gameId =>
          post {
            path("join") {
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
                  complete(StatusCodes.OK)
                }
            }
        }
    }
  }
}
