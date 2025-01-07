package weco.lambda

import grizzled.slf4j.Logging
import io.circe.Decoder
import weco.json.JsonUtil.fromJson

import scala.io.Source.stdin
import scala.util.{Failure, Success, Try}

/** Trait to deal with Newline Delimited JSON being provided on STDIN.
  *
  * Each JSON object in the input is transformed to an instance of T, according
  * to jsonToInstance (provided by the extending class) and used to populate the
  * instances Iterator.
  */

trait StdInNDJSON[T] extends Logging {
  private def jsonToInstance(jsonString: String)(
    implicit decoder: Decoder[T]
  ): Try[T] =
    fromJson[T](jsonString)

  private val stdInStrings: Iterator[String] = stdin.getLines()

  private def toInstance(
    jsonString: String
  )(implicit decoder: Decoder[T]): Option[T] =
    jsonToInstance(jsonString) match {
      case Failure(exception) =>
        error(exception.getMessage)
        None
      case Success(value) => Some(value)
    }

  protected def instances(implicit decoder: Decoder[T]): Iterator[T] =
    stdInStrings
      .flatMap(
        toInstance(_)
      )

}
