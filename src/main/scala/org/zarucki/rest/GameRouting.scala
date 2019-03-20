package org.zarucki.rest

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import cats.effect.IO
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
import scala.util.{Failure, Success}

trait GameRouting[TGameServer <: MultiPlayerGameServer[TGameServer, TGame], TGame <: TurnedBasedRestGame[
  TCommand,
  TCommandResult
], TCommand, TCommandResult]
    extends SessionSupport[UserSession] {
  protected val logger: Logger

  implicit def commandEncoder: Decoder[TCommand]
  implicit def commandResultDecoder: Encoder[TCommandResult]
  implicit def executor: ExecutionContext
  implicit def sessionCreator: SessionCreator

  val gameServerLookup: GameServerLookup[IO, UniqueId, TGameServer]

  def newGameServerForPlayer(userId: UniqueId): TGameServer

  val routes: Route = {
    pathPrefix("game") {
      pathEndOrSingleSlash {
        post {
          // TODO: what if someone comes with already valid session, should one person be able to start multiple games at once?
          val session = sessionCreator.newSession()

          extractUri { uri =>
            waitOnIOAndLogErrors(
              // TODO: check if we overwritten something? what to do then?
              (gameServerLookup.startNewGameServer(newGameServerForPlayer(session.userId)) map { newGameId =>
                setGameSession(session) {
                  complete(GameInvitation(uri.withPath(Path(s"/game/$newGameId/join")).toString()))
                }
              })
            )(s"Error while creating new game: $uri")
          }
        }
      } ~
        pathPrefix(JavaUUID) { gameUUID =>
          path("join") {
            post {
              optionalSession(oneOff, usingHeaders) { sessionOpt =>
                waitOnIOAndLogErrors(
                  gameServerLookup
                    .getGameServerById(gameUUID)
                    .flatMap {
                      case None => IO(complete(StatusCodes.NotFound))
                      case Some(gameServer) =>
                        if (sessionOpt.exists(session => gameServer.playerIdSet(session.userId))) {
                          // TODO: maybe return game status?
                          IO(returnRestGameError(RestGameErrors.alreadyJoinedGame))
                        } else {
                          if (gameServer.howManyPlayersCanStillJoin == 0) {
                            IO(returnRestGameError(RestGameErrors.gameFull))
                          } else {
                            val playerSession = sessionOpt.getOrElse(sessionCreator.newSession())

                            val result = gameServerLookup
                              .updateGameServer(
                                gameUUID,
                                _.joinPlayer(playerSession.userId)
                              )
                              .map { newGameServer =>
                                if (gameServer.howManyPlayersCanStillJoin > 1) {
                                  complete(awaitingPlayersStatus)
                                } else {
                                  complete(
                                    newGameServer.getGame().getStatus(gameServer.getPlayerNumber(playerSession.userId))
                                  )
                                }
                              }

                            result.map { route =>
                              if (sessionOpt.isDefined) {
                                route
                              } else {
                                setGameSession(playerSession) {
                                  route
                                }
                              }
                            }
                          }
                        }
                    }
                )(s"Error while joining to game $gameUUID.")
              }
            }
          } ~
            pathEndOrSingleSlash {
              put {
                entity(as[TCommand]) { command =>
                  requireSessionAndCheckIfPlayerIsPartOfGame(gameUUID) {
                    case (session, gameServer, playerNumber) =>
                      gameServer.getGame().issueCommand(playerNumber, command) match {
                        case Right(commandResult) => complete(commandResult)
                        case Left(gameError) =>
                          logger.warn(s"${session.userId} issued invalid command: " + command + " error: " + gameError)
                          returnRestGameError(gameError)
                      }
                  }
                }
              } ~
                get {
                  requireSessionAndCheckIfPlayerIsPartOfGame(gameUUID) {
                    case (_, gameServer, playerNumber) =>
                      if (gameServer.howManyPlayersCanStillJoin > 0) {
                        complete(awaitingPlayersStatus)
                      } else {
                        complete(gameServer.getGame().getStatus(playerNumber))
                      }
                  }
                }
            }
        }
    }
  }

  protected def awaitingPlayersStatus: TurnedBasedGameStatus = {
    new TurnedBasedGameStatus(gameStatus = AwaitingPlayers, yourScore = 0, opponentScore = 0)
  }

  protected def setGameSession(session: UserSession): Directive0 =
    SessionDirectives.setSession(oneOff, usingHeaders, session)

  protected def requireSessionAndCheckIfPlayerIsPartOfGame(
      gameId: UniqueId
  )(action: ((UserSession, TGameServer, Int)) => Route): Route = {
    requiredSession(oneOff, usingHeaders) { session =>
      onComplete(
        gameServerLookup
          .getGameServerById(gameId)
          .map { gameServerOpt =>
            gameServerOpt
              .filter(_.playerIdSet(session.userId))
              .map { gameServer =>
                (session, gameServer, gameServer.getPlayerNumber(session.userId))
              }
              .map(action)
          }
          .unsafeToFuture()
      ) {
        case Success(Some(value)) => value
        case Success(None)        => reject(AuthorizationFailedRejection)
        case Failure(exception) =>
          logger.error(s"Error while operating on game $gameId.", exception)
          complete(StatusCodes.ServerError)
      }
    }
  }

  protected def waitOnIOAndLogErrors(io: IO[Route])(msg: String = "Async error."): Route = {
    onComplete(io.unsafeToFuture()) {
      case Success(value) => value
      case Failure(exception) =>
        logger.error(msg, exception)
        complete(StatusCodes.ServerError)
    }
  }

  protected def returnRestGameError(restGameError: RestGameError): StandardRoute = {
    complete(restGameError.clientError -> restGameError.gameError)
  }
}

case class GameInvitation(invitationLink: String)
