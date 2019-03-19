package org.zarucki.game.battleship

/**
	*    x0,y0 is top left corner
	*
	*       x0 x1 x2 x3 x4
	*  y0
	*  y1
	*  y2
	*  y3
	* @param x
	* @param y
	*/
case class BoardAddress(x: Int, y: Int) {
  def moveInDirection(direction: Direction) = {
    direction match {
      case North => copy(y = y - 1)
      case East  => copy(x = x + 1)
      case South => copy(y = y + 1)
      case West  => copy(x = x - 1)
    }
  }
}
