package org.zarucki.rest

import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.extras.encoding.EnumerationEncoder
import io.circe.generic.extras.semiauto._
import shapeless.labelled.FieldType
import shapeless.{:+:, Coproduct, HNil, Inl, Inr, LabelledGeneric, Witness}

import scala.annotation.tailrec
import scala.util.Try

object GameStatus {
  private implicit def encodeEnumerationCConsWithUpperCamelCase[K <: Symbol, V, R <: Coproduct](
      implicit
      wit: Witness.Aux[K],
      gv: LabelledGeneric.Aux[V, HNil],
      dr: EnumerationEncoder[R]
  ): EnumerationEncoder[FieldType[K, V] :+: R] = new EnumerationEncoder[FieldType[K, V] :+: R] {
    def apply(a: FieldType[K, V] :+: R): Json = a match {
      case Inl(l) => Json.fromString(camel2Underscores(wit.value.name).toUpperCase())
      case Inr(r) => dr(r)
    }
  }

  implicit val gameStatusEncoder = deriveEnumerationEncoder[GameStatus]

  implicit val gameStatusDecoder = new Decoder[GameStatus] {
    override def apply(c: HCursor): Result[GameStatus] =
      Decoder.decodeString.apply(c).flatMap { className =>
        Try {
          import scala.reflect.runtime.universe
          val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)
          val module = runtimeMirror.staticModule("org.zarucki.rest." + underscores2camel(className))
          val obj = runtimeMirror.reflectModule(module)
          obj.instance.asInstanceOf[GameStatus]
        }.toEither.left.map {
          DecodingFailure.fromThrowable(_, List())
        }
      }

  }

  private def camel2Underscores(s: String): String = {
    @tailrec def camel2Underscore(s: String, output: String, lastUppercase: Boolean): String =
      if (s.isEmpty) output
      else {
        val firstChar = s.head
        camel2Underscore(
          s.tail,
          output + (if (firstChar.isUpper && !lastUppercase) "_" + firstChar.toLower else firstChar.toLower),
          firstChar.isUpper && !lastUppercase
        )
      }

    camel2Underscore(s, "", true)
  }

  private def underscores2camel(name: String): String = {
    assert(!(name endsWith "_"), "names ending in _ not supported by this algorithm")
    "[A-Za-z\\d]+_?|_".r.replaceAllIn(name, { x =>
      val x0 = x.group(0)
      if (x0 == "_") x0
      else x0.stripSuffix("_").toLowerCase.capitalize
    })
  }
}

sealed trait GameStatus

case object AwaitingPlayers extends GameStatus

case object YouWon extends GameStatus

case object YouLost extends GameStatus

sealed trait TurnBasedGameStatus extends GameStatus

case object WaitingForOpponentMove extends TurnBasedGameStatus

case object YourTurn extends TurnBasedGameStatus
