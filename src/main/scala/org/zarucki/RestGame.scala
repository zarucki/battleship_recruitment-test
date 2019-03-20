package org.zarucki

case class TurnedBasedGameStatus(gameStatus: GameStatus)

trait RestGame[Command, CommandResult] {
  def getStatus(playerNumber: Int): TurnedBasedGameStatus
  def issueCommand(byPlayerNumber: Int, command: Command): CommandResult
}
