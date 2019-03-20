package org.zarucki.rest
import org.zarucki.UniqueId

trait MultiPlayerGameServer[GameServer <: MultiPlayerGameServer[GameServer, Game], Game] {
  def playerIdSet: Set[UniqueId]
  def howManyPlayersCanStillJoin: Int
  def joinPlayer(playerId: UniqueId): GameServer
  def getPlayerNumber(playerId: UniqueId): Int
  def getGame(): Game
}

// TODO: generalize this to N players?
case class TwoPlayersGameServer[Game](hostPlayerId: UniqueId, game: Game, otherPlayerId: Option[UniqueId] = None)
    extends MultiPlayerGameServer[TwoPlayersGameServer[Game], Game] {

  lazy val playerIdSet = Set(hostPlayerId) ++ otherPlayerId.toSet

  override def joinPlayer(playerId: UniqueId): TwoPlayersGameServer[Game] = {
    copy(otherPlayerId = Some(playerId))
  }
  override def getGame(): Game = game

  override def getPlayerNumber(playerId: UniqueId): Int = {
    if (playerId == hostPlayerId) {
      0
    } else {
      1
    }
  }

  override def howManyPlayersCanStillJoin: Int = otherPlayerId.map(_ => 0).getOrElse(1)
}
