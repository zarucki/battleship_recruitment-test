package org.zarucki.game.battleship

class PreSetBattleshipGame(sizeX: Int, sizeY: Int) extends BattleshipGame(sizeX = sizeX, sizeY = sizeY) {
  placeShip(0, ShipLocation(North, BoardAddress(x = 0, y = 0)), OneLinerShip.oneDecker)
  placeShip(0, ShipLocation(West, BoardAddress(x = 0, y = 3)), OneLinerShip.twoDecker)
  placeShip(0, ShipLocation(West, BoardAddress(x = 0, y = 6)), OneLinerShip.threeDecker)
  placeShip(0, ShipLocation(West, BoardAddress(x = 0, y = 9)), OneLinerShip.fourDecker)

  placeShip(1, ShipLocation(North, BoardAddress(x = 0, y = 0)), OneLinerShip.fourDecker)
  placeShip(1, ShipLocation(West, BoardAddress(x = 7, y = 3)), OneLinerShip.threeDecker)
  placeShip(1, ShipLocation(West, BoardAddress(x = 8, y = 6)), OneLinerShip.twoDecker)
  placeShip(1, ShipLocation(West, BoardAddress(x = 9, y = 9)), OneLinerShip.oneDecker)

  startGame()
}
