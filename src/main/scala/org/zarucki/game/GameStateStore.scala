package org.zarucki.game
import java.util.concurrent.ConcurrentHashMap

import org.zarucki.UniqueId

// TODO: wrap this in IO?
trait GameStateStore[GameState] {
  def getGameById(id: UniqueId): Option[GameState]
  def saveNewGame(id: UniqueId, newGame: GameState): Option[GameState]
  def updateGameState(id: UniqueId, updateAction: GameState => GameState): GameState
  def clear(): Unit
}

class InMemoryGameStateStore[GameState] extends GameStateStore[GameState] {
  private val concurrentStorage = new ConcurrentHashMap[UniqueId, GameState]()

  override def getGameById(id: UniqueId): Option[GameState] =
    Option(concurrentStorage.get(id))

  override def saveNewGame(id: UniqueId, newGame: GameState): Option[GameState] =
    Option(concurrentStorage.put(id, newGame))

  override def updateGameState(id: UniqueId, updateAction: GameState => GameState): GameState =
    concurrentStorage.computeIfPresent(id, (_: UniqueId, u: GameState) => updateAction(u))

  override def clear(): Unit = concurrentStorage.clear()
}
