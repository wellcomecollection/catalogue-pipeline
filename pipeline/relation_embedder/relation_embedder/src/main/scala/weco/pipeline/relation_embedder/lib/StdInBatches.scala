package weco.pipeline.relation_embedder.lib
import weco.json.JsonUtil._
import weco.pipeline.relation_embedder.models.Batch

import scala.io.Source.stdin
import scala.util.Try

/** Trait to deal with Newline Delimited JSON being provided on STDIN.
  *
  * Each JSON object in the input is transformed to an instance of T, according
  * to jsonToInstance (provided by the extending class) and used to populate the
  * instances Iterator.
  */

trait StdInNDJSON[T] {
  protected def jsonToInstance(str: String): Try[T]
  private val stdInStrings: Iterator[String] = stdin.getLines()

  private def toInstance(jsonString: String): Option[T] =
    jsonToInstance(jsonString).toOption

  protected val instances: Iterator[T] =
    stdInStrings
      .flatMap(
        toInstance
      )

}

trait StdInBatches extends StdInNDJSON[Batch] {
  def jsonToInstance(jsonString: String): Try[Batch] =
    fromJson[Batch](jsonString)

  protected val batches: Iterator[Batch] = instances
}
