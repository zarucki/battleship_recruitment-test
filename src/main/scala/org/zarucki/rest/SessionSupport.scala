package org.zarucki.rest
import java.util.UUID

import com.softwaremill.session.{MultiValueSessionSerializer, SessionDirectives, SessionManager, SessionSerializer}
import org.zarucki.UniqueId

import scala.util.Try

trait SessionSupport[A] {
  implicit def sessionManager: SessionManager[A]

}

case class GameSession(playerId: UniqueId, gameId: UniqueId)

object GameSession {
  implicit val serializer: SessionSerializer[GameSession, String] =
    new MultiValueSessionSerializer[GameSession](
      (t: GameSession) => Map("userid" -> t.playerId.toString, "gameid" -> t.gameId.toString),
      m => Try { GameSession(playerId = UUID.fromString(m("userid")), gameId = UUID.fromString(m("gameid"))) }
    )
}

trait SessionCreator {
  def newSession(): GameSession = GameSession(playerId = UUID.randomUUID(), gameId = UUID.randomUUID())
  def newSessionForGame(gameId: UniqueId) = GameSession(playerId = UUID.randomUUID(), gameId = gameId)
}
