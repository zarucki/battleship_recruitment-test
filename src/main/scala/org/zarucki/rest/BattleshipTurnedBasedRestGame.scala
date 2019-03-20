package org.zarucki.rest
import org.zarucki.game.battleship.{BattleshipGame, BoardAddress, HitCommand, HitReport}

class BattleshipTurnedBasedRestGame(battleshipGame: BattleshipGame) extends TurnedBasedRestGame[HitCommand, HitReport] {
  override def getStatus(playerNumber: Int): TurnedBasedGameStatus = {
    // TODO: check if someone won?
    if (battleshipGame.whosTurnIsIt == playerNumber) {
      TurnedBasedGameStatus(YourTurn)
    } else {
      TurnedBasedGameStatus(WaitingForOpponentMove)
    }
  }

  override def issueCommand(byPlayerNumber: Int, command: HitCommand): HitReport = {
    val address = positionToXY(command.position)
    battleshipGame.shoot(byPlayerNumber, address)
  }

  // TODO: some unit tests here?
  private def positionToXY(position: String): BoardAddress = {
    assert(position.length == 2)
    // TODO: catch exception if toInt fails?
    BoardAddress(x = position.tail.toInt - 1, y = position.head.toUpper.toInt - 'A'.toInt)
  }
}
