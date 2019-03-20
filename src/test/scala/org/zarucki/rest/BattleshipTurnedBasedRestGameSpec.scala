package org.zarucki.rest
import org.scalatest.BeforeAndAfterEach
import org.zarucki.BaseSpec
import org.zarucki.game.battleship._

class BattleshipTurnedBasedRestGameSpec extends BaseSpec with BeforeAndAfterEach {
  var underlyingGame: BattleshipGame = _
  var sut: BattleshipTurnedBasedRestGame = _

  override protected def beforeEach(): Unit = {
    underlyingGame = new BattleshipGame(sizeX = 10, sizeY = 10)
    sut = new BattleshipTurnedBasedRestGame(underlyingGame)
  }

  it should "sink the ship after shooting all segments using string position" in {
    underlyingGame.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip.fourDecker)
    sut.issueCommand(1, HitCommand("A1")) shouldEqual Hit(OneLinerShip.fourDecker.name, sunken = false)
    sut.issueCommand(1, HitCommand("B1")) shouldEqual Hit(OneLinerShip.fourDecker.name, sunken = false)
    sut.issueCommand(1, HitCommand("C1")) shouldEqual Hit(OneLinerShip.fourDecker.name, sunken = false)
    sut.issueCommand(1, HitCommand("D1")) shouldEqual Hit(OneLinerShip.fourDecker.name, sunken = true)
  }

  it should "still be the player turn if he hits the ship" in {
    underlyingGame.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip.fourDecker)
    sut.getStatus(1) shouldEqual TurnedBasedGameStatus(YourTurn)
    sut.issueCommand(1, HitCommand("A1")) shouldEqual Hit(OneLinerShip.fourDecker.name, sunken = false)
    sut.getStatus(1) shouldEqual TurnedBasedGameStatus(YourTurn)
  }

  it should "switch turn to other player if the current player misses" in {
    underlyingGame.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip.fourDecker)
    sut.getStatus(1) shouldEqual TurnedBasedGameStatus(YourTurn)
    sut.issueCommand(1, HitCommand("A2")) shouldEqual Miss
    sut.getStatus(1) shouldEqual TurnedBasedGameStatus(WaitingForOpponentMove)
  }
}
