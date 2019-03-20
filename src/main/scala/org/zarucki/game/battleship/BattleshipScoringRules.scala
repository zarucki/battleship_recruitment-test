package org.zarucki.game.battleship

trait BattleshipScoringRules {
  def scoreForBoard(board: Board): Int
}

class EveryShipAndShipSegmentEqualBattleshipScoring(scorePerSegment: Int) extends BattleshipScoringRules {
  override def scoreForBoard(board: Board): Int = {
    board.ships.map { ship =>
      val shipHitCount = (0 until ship.getShipSegmentCount).map(ship.isShipSegmentDestroyed).filter(identity).size
      shipHitCount * scorePerSegment
    }.sum
  }
}
