package org.zarucki.rest
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes.ClientError

object RestGameErrors {
  val gameFull = RestGameError(StatusCodes.Forbidden, GameError("Game already full."))
  val alreadyJoinedGame = RestGameError(StatusCodes.BadRequest, GameError("Already joined game."))
  val invalidPosition = RestGameError(StatusCodes.BadRequest, GameError("Given position is invalid."))
  val positionOutsideOfGamePlayArea =
    RestGameError(StatusCodes.BadRequest, GameError("Position outside of gameplay area."))
}

case class GameError(message: String)
case class RestGameError(clientError: ClientError, gameError: GameError)
