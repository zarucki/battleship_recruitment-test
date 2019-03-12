package org.zarucki.rest
import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Directive1, Route}
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.softwaremill.session.{MultiValueSessionSerializer, SessionDirectives, SessionManager, SessionSerializer}
import org.zarucki.rest.BattleshipSession.UniqueId

import scala.util.Try

trait SessionSupport[A] {
  implicit def sessionManager: SessionManager[A]

}

abstract class BattleshipSessionSupport extends SessionSupport[BattleshipSession] {
  protected def setGameSession(session: BattleshipSession): Directive0 =
    SessionDirectives.setSession(oneOff, usingHeaders, session)

  protected def requiredSessionForGame(gameId: UniqueId): Directive1[BattleshipSession] =
    requiredSession(oneOff, usingHeaders).flatMap { session =>
      if (session.gameId == gameId) {
        provide(session)
      } else {
        reject(oneOff.clientSessionManager.sessionMissingRejection)
      }
    }
}

case class BattleshipSession(playerId: UniqueId, gameId: UniqueId)

object BattleshipSession {
  type UniqueId = java.util.UUID
  implicit val serializer: SessionSerializer[BattleshipSession, String] =
    new MultiValueSessionSerializer[BattleshipSession](
      (t: BattleshipSession) => Map("userid" -> t.playerId.toString, "gameid" -> t.gameId.toString),
      m => Try { BattleshipSession(playerId = UUID.fromString(m("userid")), gameId = UUID.fromString(m("gameid"))) }
    )
}

trait SessionCreator {
  def newSession(): BattleshipSession = BattleshipSession(playerId = UUID.randomUUID(), gameId = UUID.randomUUID())
  def newSessionForGame(gameId: UniqueId) = BattleshipSession(playerId = UUID.randomUUID(), gameId = gameId)
}
