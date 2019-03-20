package org.zarucki
package game.battleship

object BattleshipGameErrors {
  val shipDoesNotFitInBoard = BattleshipGameError("Ship does not fit in the board.")
  val shipCollidesWithOtherAlreadyPlacedShip = BattleshipGameError("Ship collides with other already placed ship.")
}

case class BattleshipGameError(msg: String)

// TODO: number of shots?
// TODO: scoring rules
// TODO: responsibilities: turn logic, score, set of ships to place
// TODO: logs of player actions: placements, shots?
// TODO: don't accept moves if not turn of player
class BattleshipGame(sizeX: Int, sizeY: Int) {
  // by default the second player starts
  private var currentTurnBelongsTo: Int = 1

  private val playerBoards = Array(new Board(sizeX, sizeY), new Board(sizeX, sizeY))

  // TODO: don't allow placing ships after game starts?
  def placeShip(currentPlayerNumber: Int, shipLocation: ShipLocation, ship: Ship): Either[BattleshipGameError, Unit] = {
    val currentPlayerBoard = getPlayerBoard(currentPlayerNumber)
    currentPlayerBoard.placeShip(shipLocation, ship)
  }

  def whoseTurnIsIt: Int = currentTurnBelongsTo

  def isBoardAddressWithinBoard(boardAddress: BoardAddress): Boolean = {
    !playerBoards.head.addressIsOutside(boardAddress)
  }

  def getScore(playerNumber: Int): Int = ???

  private def getOtherPlayerNumber(currentPlayerNumber: Int): Int = (currentPlayerNumber + 1) % 2

  private def getPlayerBoard(playerNumber: Int): Board = {
    assert(playerNumber >= 0)
    assert(playerNumber < playerBoards.size)

    playerBoards(playerNumber)
  }

  def shoot(byPlayerNumber: Int, address: BoardAddress): Option[Ship] = {
    (getPlayerBoard(getOtherPlayerNumber(byPlayerNumber)).shootAtAddress(address)).orElse {
      currentTurnBelongsTo = getOtherPlayerNumber(byPlayerNumber)
      None
    }
  }
}
