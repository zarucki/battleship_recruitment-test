package org.zarucki.rest
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.{StrictLogging}

class GameRoutingSpec extends BaseRouteSpec {
  val routes = Route.seal(new GameRouting with StrictLogging {}.routes)

  it should "work properly" in {
    Post("/game") ~> routes ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }
}
