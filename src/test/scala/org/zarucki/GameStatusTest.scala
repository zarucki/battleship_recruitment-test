package org.zarucki
import io.circe.syntax._
import io.circe.parser._

class GameStatusTest extends BaseSpec {
  it should "serialize and deserialize AwaitingPlayers" in {
    AwaitingPlayers.asInstanceOf[GameStatus].asJson.toString shouldEqual "\"AWAITING_PLAYERS\""
    decode[GameStatus]("\"AWAITING_PLAYERS\"") shouldEqual Right(AwaitingPlayers)
  }

  it should "serialize and deserialize YouWon" in {
    YouWon.asInstanceOf[GameStatus].asJson.toString shouldEqual "\"YOU_WON\""
    decode[GameStatus]("\"YOU_WON\"") shouldEqual Right(YouWon)
  }

  it should "serialize and deserialize YouLost" in {
    YouLost.asInstanceOf[GameStatus].asJson.toString shouldEqual "\"YOU_LOST\""
    decode[GameStatus]("\"YOU_LOST\"") shouldEqual Right(YouLost)
  }

  it should "serialize and deserialize WaitingForOpponentMove" in {
    WaitingForOpponentMove.asInstanceOf[GameStatus].asJson.toString shouldEqual "\"WAITING_FOR_OPPONENT_MOVE\""
    decode[GameStatus]("\"WAITING_FOR_OPPONENT_MOVE\"") shouldEqual Right(WaitingForOpponentMove)
  }

  it should "serialize and deserialize YourTurn" in {
    YourTurn.asInstanceOf[GameStatus].asJson.toString shouldEqual "\"YOUR_TURN\""
    decode[GameStatus]("\"YOUR_TURN\"") shouldEqual Right(YourTurn)
  }
}
