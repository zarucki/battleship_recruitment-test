package org.zarucki.game.battleship
import org.zarucki.BaseSpec

class BattleshipGameSpec extends BaseSpec {
  it should "not allow placing ship in the same spot twice" in {
    val game = new BattleshipGame(sizeX = 10, sizeY = 10)
    game.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(4)).isRight shouldEqual true
    game.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(4)) shouldEqual Left(
      BattleshipGameErrors.shipCollidesWithOtherAlreadyPlacedShip
    )
  }

  it should "allow placing ship in the same spot by two different players" in {
    val game = new BattleshipGame(sizeX = 10, sizeY = 10)
    game.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(4)).isRight shouldEqual true
    game.placeShip(1, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(4)).isRight shouldEqual true
  }

  it should "not allow placing ship that does not fit on the board" in {
    val game = new BattleshipGame(sizeX = 10, sizeY = 10)

    game.placeShip(0, ShipLocation(East, BoardAddress(1, 1)), OneLinerShip(4)) shouldEqual Left(
      BattleshipGameErrors.shipDoesNotFitInBoard
    )

    game.placeShip(0, ShipLocation(West, BoardAddress(8, 8)), OneLinerShip(4)) shouldEqual Left(
      BattleshipGameErrors.shipDoesNotFitInBoard
    )
  }

  it should "not allow placing ship that are colliding" in {
    val game = new BattleshipGame(sizeX = 10, sizeY = 10)
    game.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(4)).isRight shouldEqual true
    game.placeShip(0, ShipLocation(West, BoardAddress(0, 2)), OneLinerShip(2)) shouldEqual Left(
      BattleshipGameErrors.shipCollidesWithOtherAlreadyPlacedShip
    )
  }

  it should "not allow placing ship that are colliding 2" in {
    val game = new BattleshipGame(sizeX = 10, sizeY = 10)
    game.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(4)).isRight shouldEqual true
    game.placeShip(0, ShipLocation(West, BoardAddress(0, 0)), OneLinerShip(4)) shouldEqual Left(
      BattleshipGameErrors.shipCollidesWithOtherAlreadyPlacedShip
    )
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
    val game = new BattleshipGame(sizeX = 10, sizeY = 10)
    game.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip.fourDecker)
    game.shoot(1, BoardAddress(0, 0)) shouldEqual Hit(OneLinerShip.fourDecker.name, sunken = false)
    game.shoot(1, BoardAddress(0, 0)) shouldEqual Miss
  }

  it should "sink the ship after shooting all segments" in {
    val game = new BattleshipGame(sizeX = 10, sizeY = 10)
    game.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip.fourDecker)
    game.shoot(1, BoardAddress(0, 0)) shouldEqual Hit(OneLinerShip.fourDecker.name, sunken = false)
    game.shoot(1, BoardAddress(0, 1)) shouldEqual Hit(OneLinerShip.fourDecker.name, sunken = false)
    game.shoot(1, BoardAddress(0, 2)) shouldEqual Hit(OneLinerShip.fourDecker.name, sunken = false)
    game.shoot(1, BoardAddress(0, 3)) shouldEqual Hit(OneLinerShip.fourDecker.name, sunken = true)
  }
}
