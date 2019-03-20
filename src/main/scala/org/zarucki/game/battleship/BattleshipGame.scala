package org.zarucki
package game.battleship

object BattleshipGameErrors {
  val shipDoesNotFitInBoard = BattleshipGameError("Ship does not fit in the board.")
  val shipCollidesWithOtherAlreadyPlacedShip = BattleshipGameError("Ship collides with other already placed ship.")
  val turnBelongsToOtherPlayer = BattleshipGameError("Turn belongs to other player.")
  val cannotShootBeforeStartingTheGame = BattleshipGameError("Cannot shoot before staring the game.")
  val cannotShootAfterGameHasFinished = BattleshipGameError("Cannot shoot after game has finished.")
  val cannotPlaceShipsAfterGameStarted = BattleshipGameError("Cannot place ships after game started.")
}

case class BattleshipGameError(msg: String)

class BattleshipGame(
    sizeX: Int,
    sizeY: Int,
    scoringRules: BattleshipScoringRules = new EveryShipAndShipSegmentEqualBattleshipScoring(scorePerSegment = 1)
) {
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

  def getWinner(): Option[Int] = {
    val otherPlayer = getOtherPlayerNumber(currentTurnBelongsTo)

    if (getPlayerBoard(currentTurnBelongsTo).allShipsSunk()) {
      Some(otherPlayer)
    } else if (getPlayerBoard(otherPlayer).allShipsSunk()) {
      Some(currentTurnBelongsTo)
    } else {
      None
    }
  }

  def getPlayerScores: Array[Int] = {
    playerBoards.map(scoringRules.scoreForBoard).reverse
  }

  def shoot(byPlayerNumber: Int, address: BoardAddress): Either[BattleshipGameError, Option[Ship]] = {
    if (!gameStarted) {
      Left(BattleshipGameErrors.cannotShootBeforeStartingTheGame)
    } else if (getWinner().isDefined) {
      Left(BattleshipGameErrors.cannotShootAfterGameHasFinished)
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

  def startGame(): Unit = {
    gameStarted = true
  }

  def whoseTurnIsIt: Int = currentTurnBelongsTo

  def isBoardAddressWithinBoard(boardAddress: BoardAddress): Boolean = {
    !playerBoards.head.addressIsOutside(boardAddress)
  }

  def getOtherPlayerNumber(currentPlayerNumber: Int): Int = (currentPlayerNumber + 1) % 2

  private def getPlayerBoard(playerNumber: Int): Board = {
    assert(playerNumber >= 0)
    assert(playerNumber < playerBoards.size)

    playerBoards(playerNumber)
  }
}
