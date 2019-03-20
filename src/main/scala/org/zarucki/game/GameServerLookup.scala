package org.zarucki.game
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import cats.effect.IO
import org.zarucki.UniqueId

trait GameServerLookup[F[_], GameKey, GameServer] {
  def getGameServerById(id: GameKey): F[Option[GameServer]]
  def startNewGameServer(newGame: GameServer): F[GameKey]
  def updateGameServer(id: GameKey, updateAction: GameServer => GameServer): F[GameServer]
  def clear(): F[Unit]
}

class InMemoryGameServerLookup[GameServer] extends GameServerLookup[IO, UniqueId, GameServer] {
  protected val concurrentStorage = new ConcurrentHashMap[UniqueId, GameServer]()

  override def getGameServerById(id: UniqueId): IO[Option[GameServer]] = {
    IO(Option(concurrentStorage.get(id)))
  }

  override def startNewGameServer(newGame: GameServer): IO[UniqueId] = {
    IO {
      val newGameId = UUID.randomUUID()
      Option(concurrentStorage.put(newGameId, newGame))
      newGameId
    }
  }

  override def updateGameServer(id: UniqueId, updateAction: GameServer => GameServer): IO[GameServer] = {
    IO(concurrentStorage.computeIfPresent(id, (_: UniqueId, u: GameServer) => updateAction(u)))
  }

  override def clear(): IO[Unit] = IO(concurrentStorage.clear())
}
