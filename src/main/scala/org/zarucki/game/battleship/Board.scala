package org.zarucki.game.battleship

case class ShipLocation(bowDirection: Direction, address: BoardAddress)

// TODO: should this be immutable
class Board(sizeX: Int, sizeY: Int) {
  assert(sizeX > 0)
  assert(sizeY > 0)

  // TODO: should this be mutable
  // TODO: should this even be a map?
  private var placedShips: Map[ShipLocation, Ship] = Map()

  def addressIsOutside(address: BoardAddress): Boolean = {
    address.x < 0 || address.x >= sizeX || address.y < 0 || address.y >= sizeY
  }

  def shootAtAddress(address: BoardAddress): Option[Ship] = {
    if (addressIsOutside(address)) {
      None
    } else {
      getShipFromAddress(address).flatMap {
        case (shipLocation, ship, segmentIndex) =>
          if (ship.isShipSegmentDestroyed(segmentIndex)) {
            None // TODO: return something to mark overkill?
          } else {
            val newShip = ship.shipWithTargetSegmentHit(segmentIndex)
            placedShips = placedShips.updated(shipLocation, newShip)
            Some(newShip)
          }
      }
    }
  }

  def placeShip(shipLocation: ShipLocation, ship: Ship): Either[BattleshipGameError, Unit] = {
    val potentiallyOccupiedPieces = ship.getAllOccupiedAddresses(shipLocation)
    if (potentiallyOccupiedPieces.exists(addressIsOutside)) {
      Left(BattleshipGameErrors.shipDoesNotFitInBoard)
    } else if (potentiallyOccupiedPieces.exists(
                 address => getShipFromAddress(address).isDefined
               )) {
      Left(BattleshipGameErrors.shipCollidesWithOtherAlreadyPlacedShip)
    } else {
      placedShips = placedShips.updated(shipLocation, ship)
      Right(())
    }
  }

  protected def getShipFromAddress(address: BoardAddress): Option[(ShipLocation, Ship, Int)] = {
    placedShips.toList.view
      .map {
        case (shipLocation, ship) =>
          (shipLocation, ship, ship.getAllOccupiedAddresses(shipLocation).indexOf(address))
      }
      .find { case (_, _, segmentIndex) => segmentIndex != -1 }
  }
}
