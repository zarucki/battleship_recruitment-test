package org.zarucki
package game.battleship

object BattleshipGameErrors {
  val shipDoesNotFitInBoard = BattleshipGameError("Ship does not fit in the board.")
  val shipCollidesWithOtherAlreadyPlacedShip = BattleshipGameError("Ship collides with other already placed ship.")
}

case class BattleshipGameError(msg: String)
case class ConfirmedHit(shipType: String, isSunken: Boolean)

// TODO: number of shots?
// TODO: scoring rules
// TODO: responsibilities: turn logic, score, set of ships to place
// TODO: logs of player actions: placements, shots?
// TODO: don't accept moves if not turn of player
class BattleshipGame(sizeX: Int, sizeY: Int) extends RestGame {
  // by default the second player starts
  private var currentTurnBelongsTo: Int = 1

  private val playerBoards = Array(new Board(sizeX, sizeY), new Board(sizeX, sizeY))

  // TODO: don't allow placing ships after game starts?
  def placeShip(currentPlayerNumber: Int, shipLocation: ShipLocation, ship: Ship): Either[BattleshipGameError, Unit] = {
    val currentPlayerBoard = getPlayerBoard(currentPlayerNumber)
    currentPlayerBoard.placeShip(shipLocation, ship)
  }

  def whosTurnIsIt: Int = currentTurnBelongsTo

  def getScore(playerNumber: Int): Int = ???

  def shoot(currentPlayerNumber: Int, address: BoardAddress): Option[ConfirmedHit] = {
    (getPlayerBoard(getOtherPlayerNumber(currentPlayerNumber)).shootAtAddress(address) map { hitShip =>
      ConfirmedHit(hitShip.name, hitShip.isSunk())
    }).orElse {
      currentTurnBelongsTo = getOtherPlayerNumber(currentPlayerNumber)
      None
    }
  }

  private def getOtherPlayerNumber(currentPlayerNumber: Int) = {
    (currentPlayerNumber + 1) % 2
  }

  private def getPlayerBoard(playerNumber: Int) = {
    assert(playerNumber >= 0)
    assert(playerNumber < playerBoards.size)

    playerBoards(playerNumber)
  }

  override def getStatus(playerNumber: Int): TurnedBasedGameStatus = {
    // TODO: check if someone won?
    if (currentTurnBelongsTo == playerNumber) {
      TurnedBasedGameStatus(YourTurn)
    } else {
      TurnedBasedGameStatus(WaitingForOpponentMove)
    }
  }
}
