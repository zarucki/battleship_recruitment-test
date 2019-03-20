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
    sut.issueCommand(1, HitCommand("A1")) shouldEqual Right(Hit(OneLinerShip.fourDecker.name, sunken = false))
    sut.issueCommand(1, HitCommand("B1")) shouldEqual Right(Hit(OneLinerShip.fourDecker.name, sunken = false))
    sut.issueCommand(1, HitCommand("C1")) shouldEqual Right(Hit(OneLinerShip.fourDecker.name, sunken = false))
    sut.issueCommand(1, HitCommand("D1")) shouldEqual Right(Hit(OneLinerShip.fourDecker.name, sunken = true))
  }

  it should "still be the player turn if he hits the ship" in {
    underlyingGame.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip.fourDecker)
    sut.getStatus(1) shouldEqual TurnedBasedGameStatus(YourTurn)
    sut.issueCommand(1, HitCommand("A1")) shouldEqual Right(Hit(OneLinerShip.fourDecker.name, sunken = false))
    sut.getStatus(1) shouldEqual TurnedBasedGameStatus(YourTurn)
  }

  it should "switch turn to other player if the current player misses" in {
    underlyingGame.placeShip(0, ShipLocation(North, BoardAddress(0, 0)), OneLinerShip.fourDecker)
    sut.getStatus(1) shouldEqual TurnedBasedGameStatus(YourTurn)
    sut.issueCommand(1, HitCommand("A2")) shouldEqual Right(Miss)
    sut.getStatus(1) shouldEqual TurnedBasedGameStatus(WaitingForOpponentMove)
  }

  it should "should allow command in lowercase" in {
    sut.issueCommand(1, HitCommand("b5")).isRight shouldEqual true
  }

  it should "should correctly return error if position in hit command is incorrect" in {
    sut.issueCommand(1, HitCommand("A")) shouldEqual Left(GameErrors.invalidPosition)
    sut.issueCommand(1, HitCommand("1")) shouldEqual Left(GameErrors.invalidPosition)
    sut.issueCommand(1, HitCommand("AB2")) shouldEqual Left(GameErrors.invalidPosition)
    sut.issueCommand(1, HitCommand("12")) shouldEqual Left(GameErrors.invalidPosition)
    sut.issueCommand(1, HitCommand("2A")) shouldEqual Left(GameErrors.invalidPosition)
  }

  it should "should correctly return error if position in hit command is outside of the board" in {
    sut.issueCommand(1, HitCommand("A100")) shouldEqual Left(GameErrors.positionOutsideOfGamePlayArea)
  }
}
