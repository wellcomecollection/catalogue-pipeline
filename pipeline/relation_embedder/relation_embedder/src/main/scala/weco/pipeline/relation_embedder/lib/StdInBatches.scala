package weco.pipeline.relation_embedder.lib
import weco.json.JsonUtil._
import weco.pipeline.relation_embedder.models.Batch

import scala.io.Source.stdin
import scala.util.Try

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
