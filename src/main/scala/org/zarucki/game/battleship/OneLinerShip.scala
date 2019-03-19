package org.zarucki.game.battleship
import scala.annotation.tailrec

object OneLinerShip {
  def fourDecker = OneLinerShip(4, "FOUR_DECKER")
  def threeDecker = OneLinerShip(3, "THREE_DECKER")
  def twoDecker = OneLinerShip(2, "TWO_DECKER")
  def oneDecker = OneLinerShip(1, "ONE_DECKER")
}

case class OneLinerShip(length: Int, name: String = "", segmentsOnFire: Set[Int] = Set()) extends Ship {
  assert(length > 0)

  override def getAllOccupiedAddresses(assumingLocation: ShipLocation): Vector[BoardAddress] = {
    @tailrec
    def allOccupiedAddresses(
        ship: OneLinerShip,
        shipLocation: ShipLocation,
        alreadyOccupiedAddresses: Vector[BoardAddress]
    ): Vector[BoardAddress] = {
      if (ship.length == 1) {
        alreadyOccupiedAddresses :+ shipLocation.address
      } else {
        allOccupiedAddresses(
          ship.copy(ship.length - 1),
          shipLocation.copy(address = shipLocation.address.moveInDirection(shipLocation.bowDirection.opposite)),
          alreadyOccupiedAddresses :+ shipLocation.address
        )
      }
    }

    allOccupiedAddresses(this, assumingLocation, Vector())
  }

  override def isSunk(): Boolean = {
    segmentsOnFire.size == length
  }

  override def shipWithTargetSegmentHit(shipSegmentIndex: Int): Ship = {
    copy(segmentsOnFire = segmentsOnFire + shipSegmentIndex)
  }

  override def isShipSegmentDestroyed(shipSegmentIndex: Int): Boolean = {
    segmentsOnFire(shipSegmentIndex)
  }
}
