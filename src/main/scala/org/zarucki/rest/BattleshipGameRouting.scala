package org.zarucki
package rest
import cats.effect.IO
import com.softwaremill.session.{HeaderConfig, SessionConfig, SessionManager}
import com.typesafe.scalalogging.StrictLogging
import org.zarucki.game.battleship.PreSetBattleshipGame
import org.zarucki.game.{GameServerLookup, InMemoryGameServerLookup}

import scala.concurrent.ExecutionContext

class BattleshipGameRouting(appConfig: AppConfig)(implicit executionContext: ExecutionContext)
    extends GameRouting[TwoPlayersGameServer[BattleshipTurnedBasedRestGame], BattleshipTurnedBasedRestGame, HitCommand, HitReport]
    with HitCommandEncoding
    with StrictLogging {
  override implicit val executor: ExecutionContext = executionContext

  override implicit def sessionCreator: SessionCreator = new SessionCreator {}

  override val gameServerLookup: GameServerLookup[IO, UniqueId, TwoPlayersGameServer[BattleshipTurnedBasedRestGame]] = {
    new InMemoryGameServerLookup[TwoPlayersGameServer[BattleshipTurnedBasedRestGame]]
  }

  override def newGameServerForPlayer(userId: UniqueId): TwoPlayersGameServer[BattleshipTurnedBasedRestGame] = {
    new TwoPlayersGameServer(
      hostPlayerId = userId,
      game = new BattleshipTurnedBasedRestGame(
        new PreSetBattleshipGame(sizeX = appConfig.boardXSize, sizeY = appConfig.boardYSize)
      )
    )
  }

  override implicit val sessionManager: SessionManager[UserSession] = {
    new SessionManager[UserSession](
      SessionConfig
        .defaultConfig(appConfig.sessionSecret64CharacterLong)
        .copy(sessionHeaderConfig = HeaderConfig(appConfig.sessionHeaderName, appConfig.sessionHeaderName))
    )
  }
}
