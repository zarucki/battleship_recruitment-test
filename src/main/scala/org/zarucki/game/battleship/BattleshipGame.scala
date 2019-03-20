package org.zarucki
package game.battleship
import io.circe.{Decoder, Encoder, Json, JsonObject}
import io.circe.generic.auto._
import io.circe.syntax._
import cats.syntax.functor._

object BattleshipGameErrors {
  val shipDoesNotFitInBoard = BattleshipGameError("Ship does not fit in the board.")
  val shipCollidesWithOtherAlreadyPlacedShip = BattleshipGameError("Ship collides with other already placed ship.")
}

case class BattleshipGameError(msg: String)

case class HitCommand(position: String)

object HitReport {
  implicit val encodeHitReport: Encoder[HitReport] = Encoder.instance {
    case h @ Hit(_, _) => h.asJson.mapObject(_.add("result", Json.fromString("HIT")))
    case Miss          => Json.fromJsonObject(JsonObject.apply("result" -> Json.fromString("MISS")))
  }

  implicit val decodeHitReport: Decoder[HitReport] = Decoder[Hit].widen or Decoder[Miss.type].widen
}

trait HitReport
case class Hit(shipType: String, sunken: Boolean) extends HitReport
case object Miss extends HitReport

// TODO: number of shots?
// TODO: scoring rules
// TODO: responsibilities: turn logic, score, set of ships to place
// TODO: logs of player actions: placements, shots?
// TODO: don't accept moves if not turn of player
class BattleshipGame(sizeX: Int, sizeY: Int) {
  // by default the second player starts
  private var currentTurnBelongsTo: Int = 1

  private val playerBoards = Array(new Board(sizeX, sizeY), new Board(sizeX, sizeY))

  // TODO: don't allow placing ships after game starts?
  def placeShip(currentPlayerNumber: Int, shipLocation: ShipLocation, ship: Ship): Either[BattleshipGameError, Unit] = {
    val currentPlayerBoard = getPlayerBoard(currentPlayerNumber)
    currentPlayerBoard.placeShip(shipLocation, ship)
  }

  def whosTurnIsIt: Int = currentTurnBelongsTo

  def getScore(playerNumber: Int): Int = ???

  private def getOtherPlayerNumber(currentPlayerNumber: Int) = {
    (currentPlayerNumber + 1) % 2
  }

  private def getPlayerBoard(playerNumber: Int) = {
    assert(playerNumber >= 0)
    assert(playerNumber < playerBoards.size)

    playerBoards(playerNumber)
  }

  def shoot(byPlayerNumber: Int, address: BoardAddress): HitReport = {
    (getPlayerBoard(getOtherPlayerNumber(byPlayerNumber)).shootAtAddress(address) map { hitShip =>
      Hit(hitShip.name, hitShip.isSunk())
    }).getOrElse {
      currentTurnBelongsTo = getOtherPlayerNumber(byPlayerNumber)
      Miss
    }
  }
}
