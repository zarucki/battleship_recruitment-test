package org.zarucki.game.battleship
import org.scalatest.BeforeAndAfterEach
import org.zarucki.BaseSpec

class BattleshipGameSpec extends BaseSpec with BeforeAndAfterEach {
  var sut: BattleshipGame = _

  override protected def beforeEach(): Unit = {
    sut = new BattleshipGame(sizeX = 10, sizeY = 10)
    sut.placeShip(0, ShipLocation(North, BoardAddress(x = 8, y = 0)), OneLinerShip(4))
    sut.placeShip(1, ShipLocation(North, BoardAddress(x = 9, y = 0)), OneLinerShip(4))
  }

  it should "not allow placing ship in the same spot twice" in {
    sut.placeShip(0, ShipLocation(North, BoardAddress(x = 3, y = 0)), OneLinerShip(4)).isRight shouldEqual true
    sut.placeShip(0, ShipLocation(North, BoardAddress(x = 3, y = 0)), OneLinerShip(4)) shouldEqual Left(
      BattleshipGameErrors.shipCollidesWithOtherAlreadyPlacedShip
    )
  }

  it should "allow placing ship in the same spot by two different players" in {
    sut.placeShip(0, ShipLocation(North, BoardAddress(x = 3, y = 0)), OneLinerShip(4)).isRight shouldEqual true
    sut.placeShip(1, ShipLocation(North, BoardAddress(x = 3, y = 0)), OneLinerShip(4)).isRight shouldEqual true
  }

  it should "not allow placing ship that does not fit on the board" in {
    sut.placeShip(0, ShipLocation(East, BoardAddress(1, 1)), OneLinerShip(4)) shouldEqual Left(
      BattleshipGameErrors.shipDoesNotFitInBoard
    )

    sut.placeShip(0, ShipLocation(West, BoardAddress(8, 8)), OneLinerShip(4)) shouldEqual Left(
      BattleshipGameErrors.shipDoesNotFitInBoard
    )
  }

  it should "not allow placing ship that are colliding" in {
    sut.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(4)).isRight shouldEqual true
    sut.placeShip(0, ShipLocation(West, BoardAddress(0, 2)), OneLinerShip(2)) shouldEqual Left(
      BattleshipGameErrors.shipCollidesWithOtherAlreadyPlacedShip
    )
  }

  it should "not allow placing ship that are colliding 2" in {
    sut.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(4)).isRight shouldEqual true
    sut.placeShip(0, ShipLocation(West, BoardAddress(0, 0)), OneLinerShip(4)) shouldEqual Left(
      BattleshipGameErrors.shipCollidesWithOtherAlreadyPlacedShip
    )
  }

  it should "not allow placing ship after starting the game" in {
    sut.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(4)).isRight shouldEqual true
    sut.startGame()
    sut.placeShip(0, ShipLocation(West, BoardAddress(0, 0)), OneLinerShip(4)) shouldEqual Left(
      BattleshipGameErrors.cannotPlaceShipsAfterGameStarted
    )
  }

  it should "not allow shooting before starting the game" in {
    sut.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(4)).isRight shouldEqual true
    sut.shoot(1, BoardAddress(0, 0)) shouldEqual Left(BattleshipGameErrors.cannotShootBeforeStartingTheGame)
  }

  it should "allow for initial setup" in {
    val game = new BattleshipGame(sizeX = 10, sizeY = 10)

    game.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(1)).isRight shouldEqual true
    game.placeShip(0, ShipLocation(West, BoardAddress(0, 3)), OneLinerShip(2)).isRight shouldEqual true
    game.placeShip(0, ShipLocation(West, BoardAddress(0, 6)), OneLinerShip(3)).isRight shouldEqual true
    game.placeShip(0, ShipLocation(West, BoardAddress(0, 9)), OneLinerShip(4)).isRight shouldEqual true

    game.placeShip(1, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(4)).isRight shouldEqual true
    game.placeShip(1, ShipLocation(West, BoardAddress(7, 3)), OneLinerShip(3)).isRight shouldEqual true
    game.placeShip(1, ShipLocation(West, BoardAddress(8, 6)), OneLinerShip(2)).isRight shouldEqual true
    game.placeShip(1, ShipLocation(West, BoardAddress(9, 9)), OneLinerShip(1)).isRight shouldEqual true
  }

  it should "return miss if shooting again place that was hit before" in {
    sut.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip.fourDecker)
    sut.startGame()
    sut.shoot(1, BoardAddress(0, 0)) shouldEqual Right(Some(OneLinerShip.fourDecker.copy(segmentsOnFire = Set(0))))
    sut.shoot(1, BoardAddress(0, 0)) shouldEqual Right(None)
  }

  it should "sink the ship after shooting all segments" in {
    sut.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip.fourDecker)
    sut.startGame()
    sut.shoot(1, BoardAddress(0, 0)) shouldEqual Right(Some(OneLinerShip.fourDecker.copy(segmentsOnFire = Set(0))))
    sut.shoot(1, BoardAddress(0, 3)) shouldEqual Right(Some(OneLinerShip.fourDecker.copy(segmentsOnFire = Set(0, 3))))
    sut.shoot(1, BoardAddress(0, 1)) shouldEqual Right(
      Some(
        OneLinerShip.fourDecker.copy(segmentsOnFire = Set(0, 1, 3))
      )
    )
    sut.shoot(1, BoardAddress(0, 2)) shouldEqual Right(
      Some(
        OneLinerShip.fourDecker.copy(segmentsOnFire = Set(0, 1, 2, 3))
      )
    )
  }

  it should "should correctly figure out who won" in {
    sut.startGame()
    sut.getWinner() shouldEqual None

    sut.shoot(1, BoardAddress(x = 8, y = 0)).isRight shouldEqual true
    sut.shoot(1, BoardAddress(x = 8, y = 1)).isRight shouldEqual true
    sut.shoot(1, BoardAddress(x = 8, y = 2)).isRight shouldEqual true
    sut.shoot(1, BoardAddress(x = 8, y = 3)).isRight shouldEqual true

    sut.getWinner() shouldEqual Some(1)
  }

  it should "should correctly figure out who won 2" in {
    sut.startGame()
    sut.getWinner() shouldEqual None

    // to change turn to player 0
    sut.shoot(1, BoardAddress(x = -1, y = -1)) shouldEqual Right(None)

    sut.shoot(0, BoardAddress(x = 9, y = 0)).isRight shouldEqual true
    sut.shoot(0, BoardAddress(x = 9, y = 1)).isRight shouldEqual true
    sut.shoot(0, BoardAddress(x = 9, y = 2)).isRight shouldEqual true
    sut.shoot(0, BoardAddress(x = 9, y = 3)).isRight shouldEqual true

    sut.getWinner() shouldEqual Some(0)
  }

  it should "should not allow firing after game is finished" in {
    sut.startGame()
    sut.getWinner() shouldEqual None

    sut.shoot(1, BoardAddress(x = 8, y = 0)).isRight shouldEqual true
    sut.shoot(1, BoardAddress(x = 8, y = 1)).isRight shouldEqual true
    sut.shoot(1, BoardAddress(x = 8, y = 2)).isRight shouldEqual true
    sut.shoot(1, BoardAddress(x = 8, y = 3)).isRight shouldEqual true

    sut.getWinner().isDefined shouldEqual true

    sut.shoot(1, BoardAddress(x = 8, y = 4)) shouldEqual Left(
      BattleshipGameErrors.cannotShootAfterGameHasFinished
    )
  }

  it should "should correctly tell score" in {
    sut.startGame()
    sut.getWinner() shouldEqual None

    sut.shoot(1, BoardAddress(x = 8, y = 0)).isRight shouldEqual true
    sut.getPlayerScores shouldEqual Array(0, 1)
    sut.shoot(1, BoardAddress(x = 6, y = 0)) shouldEqual Right(None)

    sut.shoot(0, BoardAddress(x = 9, y = 0)).isRight shouldEqual true
    sut.shoot(0, BoardAddress(x = 9, y = 2)).isRight shouldEqual true
    sut.getPlayerScores shouldEqual Array(2, 1)
  }
}
