package org.zarucki.rest

case class TurnedBasedGameStatus(gameStatus: GameStatus)

trait TurnedBasedRestGame[Command, CommandResult] {
  def getStatus(playerNumber: Int): TurnedBasedGameStatus
  def issueCommand(byPlayerNumber: Int, command: Command): CommandResult
}
