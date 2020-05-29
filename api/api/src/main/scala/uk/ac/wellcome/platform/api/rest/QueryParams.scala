package uk.ac.wellcome.platform.api.rest

import java.time.LocalDate

import akka.http.scaladsl.server.{Directive, Directives, ValidationRejection}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import io.circe.{Decoder, Json}
import uk.ac.wellcome.platform.api.models.LicenseFilter
import uk.ac.wellcome.platform.api.rest.MultipleWorksParams.decodeCommaSeparated

trait QueryParams

object CommonDecoders {
  implicit val licenseFilter: Decoder[LicenseFilter] =
    decodeCommaSeparated.emap(strs => Right(LicenseFilter(strs)))
}

trait QueryParamsUtils extends Directives {

  implicit def unmarshaller[T](
    implicit decoder: Decoder[T]): Unmarshaller[String, T] =
    Unmarshaller.strict[String, T] { str =>
      decoder.decodeJson(Json.fromString(str)) match {
        case Left(err)    => throw new IllegalArgumentException(err.message)
        case Right(value) => value
      }
    }

  implicit val decodeLocalDate: Decoder[LocalDate] =
    Decoder.decodeLocalDate.withErrorMessage(
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

  def decodeOneOfCommaSeparated[T](values: (String, T)*): Decoder[List[T]] = {
    val mapping = values.toMap
    val validStrs = values.map(_._1).toList
    decodeCommaSeparated.emap { strs =>
      mapStringsToValues(strs, mapping).left
        .map { invalidStrs =>
          invalidValuesMsg(invalidStrs, validStrs)
        }
    }
  }

  def decodeIncludesAndExcludes[T](
    values: (String, T)*): Decoder[(List[T], List[T])] = {
    val mapping = values.toMap
    val validStrs = values.map(_._1).toList
    decodeCommaSeparated
      .emap { strs =>
        val (excludeStrs, includeStrs) = strs.partition(_.startsWith("!"));
        val includes = mapStringsToValues(includeStrs, mapping);
        val excludes = mapStringsToValues(excludeStrs.map(_.tail), mapping);
        (includes, excludes) match {
          case (Right(includes), Right(excludes)) => Right((includes, excludes))
          case _ =>
            val invalidStrs = (
              includes.left.getOrElse(Nil) ++ excludes.left.getOrElse(Nil)
            ).distinct
            Left(invalidValuesMsg(invalidStrs, validStrs))
        }
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

  def validated[T <: QueryParams](errors: List[String],
                                  params: T): Directive[Tuple1[T]] =
    errors match {
      case Nil => provide(params)
      case errs =>
        reject(ValidationRejection(errs.mkString(", ")))
          .toDirective[Tuple1[T]]
    }

  def mapStringsToValues[T](
    strs: List[String],
    mapping: Map[String, T]): Either[List[String], List[T]] = {
    val results = strs.map { str =>
      mapping
        .get(str)
        .map(Right(_))
        .getOrElse(Left(str))
    }
    val invalid = results.collect { case Left(error) => error }
    val valid = results.collect { case Right(value)  => value }
    (invalid, valid) match {
      case (Nil, results)     => Right(results)
      case (invalidValues, _) => Left(invalidValues)
    }
  }
}

object QueryParamsUtils extends QueryParamsUtils
