package org.zarucki.rest
import java.util.UUID

import com.softwaremill.session.{MultiValueSessionSerializer, RefreshTokenStorage, SessionManager, SessionSerializer}
import org.zarucki.rest.Session.UniqueId

import scala.util.Try

trait SessionSupport[A] {
  implicit def sessionManager: SessionManager[A]
}

case class Session(userId: UniqueId, gameId: UniqueId)

object Session {
  type UniqueId = java.util.UUID
  implicit val serializer: SessionSerializer[Session, String] = new MultiValueSessionSerializer[Session](
    (t: Session) => Map("userid" -> t.userId.toString, "gameid" -> t.gameId.toString),
    m => Try { Session(userId = UUID.fromString(m("userid")), gameId = UUID.fromString(m("gameid"))) }
  )
}

trait SessionCreator {
  def newSession(): Session = Session(userId = UUID.randomUUID(), gameId = UUID.randomUUID())
  def newSessionForGame(gameId: UniqueId) = Session(userId = UUID.randomUUID(), gameId = gameId)
}
