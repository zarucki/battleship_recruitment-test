package org.zarucki
package game.battleship

object BattleshipGameErrors {
  val shipDoesNotFitInBoard = BattleshipGameError("Ship does not fit in the board.")
  val shipCollidesWithOtherAlreadyPlacedShip = BattleshipGameError("Ship collides with other already placed ship.")
  val turnBelongsToOtherPlayer = BattleshipGameError("Turn belongs to other player.")
  val cannotShootBeforeStartingTheGame = BattleshipGameError("Cannot shoot before staring the game.")
  val cannotPlaceShipsAfterGameStarted = BattleshipGameError("Cannot place ships after game started.")
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
  private var gameStarted: Boolean = false

  private val playerBoards = Array(new Board(sizeX, sizeY), new Board(sizeX, sizeY))

  def placeShip(currentPlayerNumber: Int, shipLocation: ShipLocation, ship: Ship): Either[BattleshipGameError, Unit] = {
    if (gameStarted) {
      Left(BattleshipGameErrors.cannotPlaceShipsAfterGameStarted)
    } else {
      val currentPlayerBoard = getPlayerBoard(currentPlayerNumber)
      currentPlayerBoard.placeShip(shipLocation, ship)
    }
  }

  def startGame(): Unit = {
    gameStarted = true
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

  def shoot(byPlayerNumber: Int, address: BoardAddress): Either[BattleshipGameError, Option[Ship]] = {
    if (!gameStarted) {
      Left(BattleshipGameErrors.cannotShootBeforeStartingTheGame)
    } else if (currentTurnBelongsTo != byPlayerNumber) {
      Left(BattleshipGameErrors.turnBelongsToOtherPlayer)
    } else {
      Right(
        (getPlayerBoard(getOtherPlayerNumber(byPlayerNumber)).shootAtAddress(address)).orElse {
          currentTurnBelongsTo = getOtherPlayerNumber(byPlayerNumber)
          None
        }
      )
    }
  }
}
