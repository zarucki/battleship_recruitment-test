package org.zarucki.rest
import java.util.UUID

import com.softwaremill.session.{MultiValueSessionSerializer, SessionManager, SessionSerializer}
import org.zarucki.UniqueId

import scala.util.Try

trait SessionSupport[A] {
  implicit def sessionManager: SessionManager[A]

}

case class UserSession(userId: UniqueId)

object UserSession {
  val userIdKey = "userid"
  implicit val serializer: SessionSerializer[UserSession, String] =
    new MultiValueSessionSerializer[UserSession](
      (t: UserSession) => Map(userIdKey -> t.userId.toString),
      m => Try { UserSession(userId = UUID.fromString(m(userIdKey))) }
    )
}

trait SessionCreator {
  def newSession(): UserSession = UserSession(userId = UUID.randomUUID())
}
