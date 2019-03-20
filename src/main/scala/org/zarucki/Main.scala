package org.zarucki

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import org.zarucki.rest._

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object Main extends App with StrictLogging {

  implicit val system: ActorSystem = ActorSystem("battleship-http-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  // TODO: read this from typeconfig
  val appConfig: AppConfig = AppConfig(
    httpRestApiPort = 8080,
    sessionSecret64CharacterLong = "W8MqCPCAToiDPGBCeHscgWbJ1XsRh03U6qkDc5kjl1KTeKvAxnN3OXlukbIfzbxy",
    sessionHeaderName = "Set-Auth-Token",
    boardXSize = 10,
    boardYSize = 10
  )

  val battleshipGameRouting = new BattleshipGameRouting(appConfig)

  val bindingFuture = Http().bindAndHandle(
    handler = RoutesWrapper.routeWrappers {
      battleshipGameRouting.routes
    },
    interface = "localhost",
    port = appConfig.httpRestApiPort
  )

  logger.info(s"Server online at http://localhost:${appConfig.httpRestApiPort}/\nPress RETURN to stop...")

  StdIn.readLine() // let it run until user presses return

  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
