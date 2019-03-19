package org.zarucki.game
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import org.zarucki.UniqueId

// TODO: wrap this in IO?
trait GameServerLookup[GameKey, GameServer] {
  def getGameServerById(id: GameKey): Option[GameServer]
  def startNewGameServer(newGame: GameServer): GameKey
  def updateGameServer(id: GameKey, updateAction: GameServer => GameServer): GameServer
  def clear(): Unit
}

class InMemoryGameServerLookup[GameServer] extends GameServerLookup[UniqueId, GameServer] {
  protected val concurrentStorage = new ConcurrentHashMap[UniqueId, GameServer]()

  override def getGameServerById(id: UniqueId): Option[GameServer] =
    Option(concurrentStorage.get(id))

  override def startNewGameServer(newGame: GameServer): UniqueId = {
    val newGameId = UUID.randomUUID()
    Option(concurrentStorage.put(newGameId, newGame))
    newGameId
  }

  override def updateGameServer(id: UniqueId, updateAction: GameServer => GameServer): GameServer =
    concurrentStorage.computeIfPresent(id, (_: UniqueId, u: GameServer) => updateAction(u))

  override def clear(): Unit = concurrentStorage.clear()
}
