package org.zarucki.rest

import io.circe.{Decoder, Encoder, Json, JsonObject}
import io.circe.generic.auto._
import io.circe.syntax._
import cats.syntax.functor._

object HitReport {
  implicit val encodeHitReport: Encoder[HitReport] = Encoder.instance {
    case h @ Hit(_, _) => h.asJson.mapObject(_.add("result", Json.fromString("HIT")))
    case Miss          => Json.fromJsonObject(JsonObject.apply("result" -> Json.fromString("MISS")))
  }

  implicit val decodeHitReport: Decoder[HitReport] = Decoder[Hit].widen or Decoder[Miss.type].widen
}

trait HitReport
case class Hit(shipType: String, sunken: Boolean) extends HitReport
case object Miss extends HitReport

case class HitCommand(position: String)
