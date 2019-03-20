package org.zarucki.rest

case class TurnedBasedGameStatus(gameStatus: GameStatus, yourScore: Int = 0, opponentScore: Int = 0)

trait TurnedBasedRestGame[Command, CommandResult] {
  def getStatus(playerNumber: Int): TurnedBasedGameStatus
  def issueCommand(byPlayerNumber: Int, command: Command): Either[RestGameError, CommandResult]
}
