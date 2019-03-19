package org.zarucki.game.battleship
import org.zarucki.BaseSpec

class OneLinerShipSpec extends BaseSpec {
  val shipStartAddress = BoardAddress(x = 0, y = 0)

  it should "not matter what direction one liner of length 1 is pointing" in {
    new OneLinerShip(1).getAllOccupiedAddresses(ShipLocation(North, shipStartAddress)) shouldEqual Vector(
      shipStartAddress
    )

    new OneLinerShip(1).getAllOccupiedAddresses(ShipLocation(South, shipStartAddress)) shouldEqual Vector(
      shipStartAddress
    )

    new OneLinerShip(1).getAllOccupiedAddresses(ShipLocation(East, shipStartAddress)) shouldEqual Vector(
      shipStartAddress
    )

    new OneLinerShip(1).getAllOccupiedAddresses(ShipLocation(West, shipStartAddress)) shouldEqual Vector(
      shipStartAddress
    )
  }

  it should "go into negative numbers if it does not fit" in {
    new OneLinerShip(2).getAllOccupiedAddresses(ShipLocation(South, shipStartAddress)) shouldEqual Vector(
      shipStartAddress,
      BoardAddress(x = 0, y = -1)
    )
  }

  it should "occupy 4 addresses if length is 4" in {
    new OneLinerShip(4).getAllOccupiedAddresses(ShipLocation(North, shipStartAddress)) shouldEqual Vector(
      shipStartAddress,
      BoardAddress(x = 0, y = 1),
      BoardAddress(x = 0, y = 2),
      BoardAddress(x = 0, y = 3)
    )
  }

  it should "correctly occupy addresses if ship places somewhere in the middle" in {
    new OneLinerShip(3).getAllOccupiedAddresses(ShipLocation(North, BoardAddress(x = 3, y = 3))) shouldEqual Vector(
      BoardAddress(x = 3, y = 3),
      BoardAddress(x = 3, y = 4),
      BoardAddress(x = 3, y = 5)
    )
  }

  it should "occupy 2 length ship in all directions" in {
    new OneLinerShip(2).getAllOccupiedAddresses(ShipLocation(North, shipStartAddress)) shouldEqual Vector(
      shipStartAddress,
      BoardAddress(x = 0, y = 1)
    )

    new OneLinerShip(2).getAllOccupiedAddresses(ShipLocation(South, shipStartAddress)) shouldEqual Vector(
      shipStartAddress,
      BoardAddress(x = 0, y = -1)
    )

    new OneLinerShip(2).getAllOccupiedAddresses(ShipLocation(East, shipStartAddress)) shouldEqual Vector(
      shipStartAddress,
      BoardAddress(x = -1, y = 0)
    )

    new OneLinerShip(2).getAllOccupiedAddresses(ShipLocation(West, shipStartAddress)) shouldEqual Vector(
      shipStartAddress,
      BoardAddress(x = 1, y = 0)
    )
  }
}
