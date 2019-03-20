package org.zarucki.rest

object GameErrors {
  val gameFull = GameError("Game already full.")
  val alreadyJoinedGame = GameError("Already joined game.")
}

case class GameError(message: String)
