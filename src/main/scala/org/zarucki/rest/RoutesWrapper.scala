package org.zarucki.rest
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, ExceptionHandler, RejectionHandler}
import com.typesafe.scalalogging.StrictLogging

import scala.util.control.NonFatal

object RoutesWrapper extends StrictLogging {
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

  val routeWrappers: Directive[Unit] = handleExceptions(exceptionHandler) & logDuration
}
