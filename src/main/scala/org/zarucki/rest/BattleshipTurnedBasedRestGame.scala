package org.zarucki.rest
import org.zarucki.game.battleship.{BattleshipGame, BoardAddress, HitCommand, HitReport}

import scala.util.Try

class BattleshipTurnedBasedRestGame(battleshipGame: BattleshipGame) extends TurnedBasedRestGame[HitCommand, HitReport] {
  override def getStatus(playerNumber: Int): TurnedBasedGameStatus = {
    // TODO: check if someone won?
    if (battleshipGame.whoseTurnIsIt == playerNumber) {
      TurnedBasedGameStatus(YourTurn)
    } else {
      TurnedBasedGameStatus(WaitingForOpponentMove)
    }
  }

  override def issueCommand(byPlayerNumber: Int, command: HitCommand): Either[GameError, HitReport] = {
    positionToXY(command.position).flatMap { address =>
      if (battleshipGame.isBoardAddressWithinBoard(address)) {
        Right(battleshipGame.shoot(byPlayerNumber, address))
      } else {
        Left(GameErrors.positionOutsideOfGamePlayArea)
      }
    }
  }

  protected def positionToXY(position: String): Either[GameError, BoardAddress] = {
    if (position.size < 2 || !position.head.isLetter) {
      Left(GameErrors.invalidPosition)
    } else {
      Try(
        BoardAddress(
          x = position.tail.toInt - 1,
          y = position.head.toUpper.toInt - 'A'.toInt
        )
      ).toEither.left
        .map(_ => GameErrors.invalidPosition)
        .right
        .flatMap { boardAddress =>
          if (boardAddress.x >= 0 && boardAddress.y >= 0) {
            Right(boardAddress)
          } else {
            Left(GameErrors.invalidPosition)
          }
        }
    }
  }
}
