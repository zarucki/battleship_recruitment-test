package org.zarucki

case class TurnedBasedGameStatus(gameStatus: GameStatus)

trait RestGame {
  def getStatus(playerNumber: Int): TurnedBasedGameStatus
}
