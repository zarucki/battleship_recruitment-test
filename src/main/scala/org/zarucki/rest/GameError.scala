package org.zarucki.rest

object GameErrors {
  val gameFull = GameError("Game already full.")
  val alreadyJoinedGame = GameError("Already joined game.")
  val invalidPosition = GameError("Given position is invalid.")
  val positionOutsideOfGamePlayArea = GameError("Position outside of gameplay area.")
}

case class GameError(message: String)
