package org.zarucki.rest
import akka.http.scaladsl.model.StatusCodes
import org.zarucki.game.battleship.{BattleshipGame, BoardAddress}

import scala.util.Try

class BattleshipTurnedBasedRestGame(battleshipGame: BattleshipGame) extends TurnedBasedRestGame[HitCommand, HitReport] {
  override def getStatus(playerNumber: Int): TurnedBasedGameStatus = {
    val status = battleshipGame.getWinner() match {
      case Some(winnerPlayerNumber) if winnerPlayerNumber == playerNumber => YouWon
      case Some(_)                                                        => YouLost
      case None =>
        if (battleshipGame.whoseTurnIsIt == playerNumber) {
          YourTurn
        } else {
          WaitingForOpponentMove
        }
    }

    val playerScores = battleshipGame.getPlayerScores
    val yourScore = playerScores(playerNumber)
    val opponentScore = playerScores(battleshipGame.getOtherPlayerNumber(playerNumber))
    TurnedBasedGameStatus(status, yourScore, opponentScore)
  }

  override def issueCommand(byPlayerNumber: Int, command: HitCommand): Either[RestGameError, HitReport] = {
    positionToXY(command.position).flatMap { address =>
      if (battleshipGame.isBoardAddressWithinBoard(address)) {
        battleshipGame
          .shoot(byPlayerNumber, address)
          .map {
            case Some(ship) => Hit(shipType = ship.name, sunken = ship.isSunk())
            case None       => Miss
          }
          .left
          .map { error =>
            RestGameError(StatusCodes.BadRequest, GameError(error.msg))
          }
      } else {
        Left(RestGameErrors.positionOutsideOfGamePlayArea)
      }
    }
  }

  protected def positionToXY(position: String): Either[RestGameError, BoardAddress] = {
    if (position.size < 2 || !position.head.isLetter) {
      Left(RestGameErrors.invalidPosition)
    } else {
      Try(
        BoardAddress(
          x = position.tail.toInt - 1,
          y = position.head.toUpper.toInt - 'A'.toInt
        )
      ).toEither.left
        .map(_ => RestGameErrors.invalidPosition)
        .right
        .flatMap { boardAddress =>
          if (boardAddress.x >= 0 && boardAddress.y >= 0) {
            Right(boardAddress)
          } else {
            Left(RestGameErrors.invalidPosition)
          }
        }
    }
  }
}
