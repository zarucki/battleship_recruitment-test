package org.zarucki.rest
import java.util.UUID

import com.softwaremill.session.{MultiValueSessionSerializer, RefreshTokenStorage, SessionManager, SessionSerializer}

import scala.util.Try

trait SessionSupport[A] {
  implicit def sessionManager: SessionManager[A]
}

case class Session(userId: UUID)

object Session {
  implicit val serializer: SessionSerializer[Session, String] = new MultiValueSessionSerializer[Session](
    (t: Session) => Map("id" -> t.userId.toString),
    m => Try { Session(UUID.fromString(m("id"))) }
  )
}

trait SessionCreator {
  def getNewSession(): Session =
    Session(UUID.randomUUID())
}
