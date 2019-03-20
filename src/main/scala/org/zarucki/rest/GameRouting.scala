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
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import org.zarucki.game.GameServerLookup
import org.zarucki._

import scala.concurrent.ExecutionContext

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

  def gameServerLookup: GameServerLookup[UniqueId, TGameServer]
  def newGameServerForPlayer(userId: UniqueId): TGameServer

  // TODO: rejection should be wrapped and returned as json
  val routes: Route = {
    pathPrefix("game") {
      pathEndOrSingleSlash {
        post {
          val session = sessionCreator.newSession()

          // TODO: what if someone comes with already valid session, should one person be able to start multiple games at once?
          // TODO: check if we overwritten something? what to do then?
          val newGameId = gameServerLookup.startNewGameServer(newGameServerForPlayer(session.userId))

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
              gameServerLookup.getGameServerById(gameUUID) match {
                case None => complete(StatusCodes.NotFound)
                case Some(gameServer) =>
                  optionalSession(oneOff, usingHeaders) {
                    case Some(existingSession) if gameServer.playerIdSet(existingSession.userId) =>
                      // TODO: maybe return game status?
                      returnRestGameError(RestGameErrors.alreadyJoinedGame)
                    case existingSession =>
                      if (gameServer.howManyPlayersCanStillJoin == 0) {
                        returnRestGameError(RestGameErrors.gameFull)
                      } else {
                        val playerSession = existingSession.getOrElse(sessionCreator.newSession())

                        // TODO: potential racing / lost write problem
                        gameServerLookup.updateGameServer(
                          gameUUID,
                          _.joinPlayer(playerSession.userId)
                        )

                        val result = if (gameServer.howManyPlayersCanStillJoin > 1) {
                          complete(new TurnedBasedGameStatus(gameStatus = AwaitingPlayers))
                        } else {
                          gameServerLookup.getGameServerById(gameUUID).map {
                            _.getGame().getStatus(gameServer.getPlayerNumber(playerSession.userId))
                          } match {
                            case Some(status) => complete(status)
                            case None         => complete(StatusCodes.NotFound)
                          }
                        }

                        if (existingSession.isDefined) {
                          result
                        } else {
                          setGameSession(playerSession) {
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
                entity(as[TCommand]) { command =>
                  requireSessionAndCheckIfPlayerIsPartOfGame(gameUUID) { (session, gameServer, playerNumber) =>
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
                  requireSessionAndCheckIfPlayerIsPartOfGame(gameUUID) { (session, gameServer, playerNumber) =>
                    if (gameServer.howManyPlayersCanStillJoin > 0) {
                      complete(new TurnedBasedGameStatus(gameStatus = AwaitingPlayers))
                    } else {
                      complete(gameServer.getGame().getStatus(playerNumber))
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
  ): Directive[(UserSession, TGameServer, Int)] =
    requiredSession(oneOff, usingHeaders).flatMap { session =>
      gameServerLookup.getGameServerById(gameId) match {
        case Some(gameServer) if gameServer.playerIdSet(session.userId) =>
          tprovide((session, gameServer, gameServer.getPlayerNumber(session.userId)))
        case Some(_) => reject(AuthorizationFailedRejection)
        case None    => reject()
      }
    }

  protected def returnRestGameError(restGameError: RestGameError): StandardRoute = {
    complete(restGameError.clientError -> restGameError.gameError)
  }
}

case class GameInvitation(invitationLink: String)
