package org.zarucki.game
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import org.zarucki.UniqueId

// TODO: wrap this in IO?
trait GameStateStore[GameKey, GameState] {
  def getGameById(id: GameKey): Option[GameState]
  def saveNewGame(newGame: GameState): GameKey
  def updateGameState(id: GameKey, updateAction: GameState => GameState): GameState
  def clear(): Unit
}

class InMemoryGameStateStore[GameState] extends GameStateStore[UniqueId, GameState] {
  protected val concurrentStorage = new ConcurrentHashMap[UniqueId, GameState]()

  override def getGameById(id: UniqueId): Option[GameState] =
    Option(concurrentStorage.get(id))

  override def saveNewGame(newGame: GameState): UniqueId = {
    val newGameId = UUID.randomUUID()
    Option(concurrentStorage.put(newGameId, newGame))
    newGameId
  }

  override def updateGameState(id: UniqueId, updateAction: GameState => GameState): GameState =
    concurrentStorage.computeIfPresent(id, (_: UniqueId, u: GameState) => updateAction(u))

  override def clear(): Unit = concurrentStorage.clear()
}
