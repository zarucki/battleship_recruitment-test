package org.zarucki.rest

import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import cats.syntax.functor._

object HitReport {
  implicit val encodeHitReport: Encoder[HitReport] = Encoder.instance {
    case h @ Hit(_, _) => h.asJson.mapObject(_.add("result", Json.fromString("HIT")))
    case Miss          => Json.fromJsonObject(JsonObject.apply("result" -> Json.fromString("MISS")))
  }

  implicit val missDecoder: Decoder[Miss.type] = new Decoder[Miss.type] {

    final def apply(c: HCursor): Decoder.Result[Miss.type] = {
      c.downField("result").as[String].flatMap { resultValue =>
        if (resultValue == "MISS") {
          Right(Miss)
        } else {
          Left(DecodingFailure("Not miss object.", List()))
        }
      }
    }
  }

  implicit val decodeHitReport: Decoder[HitReport] = Decoder[Hit].widen or missDecoder.widen
}

sealed trait HitReport
case class Hit(shipType: String, sunken: Boolean) extends HitReport
case object Miss extends HitReport

case class HitCommand(position: String)
