package org.zarucki.game.battleship

sealed trait Direction {
  def opposite: Direction
}
case object North extends Direction {
  override def opposite: Direction = South
}

case object East extends Direction {
  override def opposite: Direction = West
}

case object South extends Direction {
  override def opposite: Direction = North
}

case object West extends Direction {
  override def opposite: Direction = East
}
