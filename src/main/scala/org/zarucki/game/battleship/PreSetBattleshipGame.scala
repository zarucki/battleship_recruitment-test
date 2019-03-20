package org.zarucki.game.battleship

class PreSetBattleshipGame(sizeX: Int, sizeY: Int) extends BattleshipGame(sizeX = sizeX, sizeY = sizeY) {
  placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(1))
  placeShip(0, ShipLocation(West, BoardAddress(0, 3)), OneLinerShip(2))
  placeShip(0, ShipLocation(West, BoardAddress(0, 6)), OneLinerShip(3))
  placeShip(0, ShipLocation(West, BoardAddress(0, 9)), OneLinerShip(4))

  placeShip(1, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip(4))
  placeShip(1, ShipLocation(West, BoardAddress(7, 3)), OneLinerShip(3))
  placeShip(1, ShipLocation(West, BoardAddress(8, 6)), OneLinerShip(2))
  placeShip(1, ShipLocation(West, BoardAddress(9, 9)), OneLinerShip(1))
}
