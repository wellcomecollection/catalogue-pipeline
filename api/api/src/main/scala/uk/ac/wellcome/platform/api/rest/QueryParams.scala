package uk.ac.wellcome.platform.api.rest

import java.time.LocalDate

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.unmarshalling.Unmarshaller
import io.circe.java8.time.TimeInstances
import io.circe.{Decoder, Json}

trait QueryParams

trait QueryParamsUtils extends Directives with TimeInstances {

  implicit def unmarshaller[T](
    implicit decoder: Decoder[T]): Unmarshaller[String, T] =
    Unmarshaller.strict[String, T] { str =>
      decoder.decodeJson(Json.fromString(str)) match {
        case Left(err)    => throw new IllegalArgumentException(err.message)
        case Right(value) => value
      }
    }

  implicit val decodeLocalDate: Decoder[LocalDate] =
    decodeLocalDateDefault.withErrorMessage(
      "Invalid date encoding. Expected YYYY-MM-DD"
    )

  implicit val decodeInt: Decoder[Int] =
    Decoder.decodeInt.withErrorMessage("must be a valid Integer")

  def decodeCommaSeparated: Decoder[List[String]] =
    Decoder.decodeString.emap(str => Right(str.split(",").toList))

  def decodeOneOf[T](values: (String, T)*): Decoder[T] =
    Decoder.decodeString.emap { str =>
      values.toMap
        .get(str)
        .map(Right(_))
        .getOrElse(Left(invalidValuesMsg(List(str), values.map(_._1).toList)))
    }

  def decodeOneWithDefaultOf[T](default: T, values: (String, T)*): Decoder[T] =
    Decoder.decodeString.map { values.toMap.getOrElse(_, default) }

  def decodeOneOfCommaSeparated[T](values: (String, T)*): Decoder[List[T]] =
    decodeCommaSeparated.emap { strs =>
      val mapping = values.toMap
      val results = strs.map { str =>
        mapping
          .get(str)
          .map(Right(_))
          .getOrElse(Left(str))
      }
      val invalid = results.collect { case Left(error) => error }
      val valid = results.collect { case Right(value)  => value }
      (invalid, valid) match {
        case (Nil, results) => Right(results)
        case (invalidValues, _) =>
          Left(invalidValuesMsg(invalidValues, values.map(_._1).toList))
      }
    }

  def invalidValuesMsg(values: List[String],
                       validValues: List[String]): String = {
    val oneOfMsg =
      s"Please choose one of: [${validValues.mkString("'", "', '", "'")}]"
    values match {
      case value :: Nil => s"'$value' is not a valid value. $oneOfMsg"
      case _ =>
        s"${values.mkString("'", "', '", "'")} are not valid values. $oneOfMsg"
    }
  }
}
