package org.zarucki.game.battleship

trait Ship {
  def name: String
  def getShipSegmentCount: Int
  def getAllOccupiedAddresses(assumingLocation: ShipLocation): Vector[BoardAddress]
  def shipWithTargetSegmentHit(targetShipSegmentIndex: Int): Ship
  // there could be ship that has segments with more hitpoints
  def isShipSegmentDestroyed(shipSegmentIndex: Int): Boolean
  def isSunk(): Boolean
}
