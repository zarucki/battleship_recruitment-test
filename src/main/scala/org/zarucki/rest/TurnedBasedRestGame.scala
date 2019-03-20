package org.zarucki.rest

// TODO: return scores
case class TurnedBasedGameStatus(gameStatus: GameStatus)

trait TurnedBasedRestGame[Command, CommandResult] {
  def getStatus(playerNumber: Int): TurnedBasedGameStatus
  def issueCommand(byPlayerNumber: Int, command: Command): Either[GameError, CommandResult]
}
