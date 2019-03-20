package org.zarucki
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, ExceptionHandler, RejectionHandler}
import akka.stream.ActorMaterializer
import com.softwaremill.session.{RefreshTokenStorage, SessionManager}
import com.typesafe.scalalogging.StrictLogging
import org.zarucki.game.GameServerLookup
import org.zarucki.game.battleship.BattleshipGame
import org.zarucki.rest.{GameRouting, SessionCreator, TwoPlayersGameServer, UserSession}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.io.StdIn
import scala.util.control.NonFatal

case class AppConfig(httpRestApiPort: Int)

object Main extends App with StrictLogging {

  implicit val system: ActorSystem = ActorSystem("battleship-http-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val appConfig: AppConfig = AppConfig(httpRestApiPort = 8080)

  val gamingRoutes = new GameRouting[TwoPlayersGameServer[BattleshipGame], BattleshipGame] with StrictLogging {
    override implicit def executor: ExecutionContext = executionContext
    override implicit def sessionManager: SessionManager[UserSession] = ???
    override implicit def sessionCreator: SessionCreator = ???
    override def gameServerLookup: GameServerLookup[
      UniqueId,
      TwoPlayersGameServer[BattleshipGame]
    ] = ???
    override def newGameServerForPlayer(
        userId: UniqueId
    ): TwoPlayersGameServer[BattleshipGame] = ???
  }

  val bindingFuture = Http().bindAndHandle(
    handler = routeWrappers {
      gamingRoutes.routes
    },
    interface = "localhost",
    port = appConfig.httpRestApiPort
  )

  logger.info(s"Server online at http://localhost:${appConfig.httpRestApiPort}/\nPress RETURN to stop...")

  StdIn.readLine() // let it run until user presses return

  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done

  def routeWrappers: Directive[Unit] = handleExceptions(exceptionHandler) & logDuration

  def logDuration: Directive[Unit] = {
    val rejectionHandler = RejectionHandler.default

    extractRequestContext.flatMap { ctx =>
      val start = System.currentTimeMillis()
      // handling rejections here so that we get proper status codes
      mapResponse { resp =>
        val d = System.currentTimeMillis() - start
        logger.info(s"[${resp.status.intValue()}] ${ctx.request.method.name} ${ctx.request.uri} took: ${d}ms")
        resp
      } & handleRejections(rejectionHandler)
    }
  }

  def exceptionHandler =
    ExceptionHandler {
      case NonFatal(ex: Exception) =>
        logger.error("I crashed hard.", ex)
        complete(StatusCodes.BadRequest)
    }
}
