package weco.pipeline.relation_embedder.lib
import weco.json.JsonUtil._
import weco.pipeline.relation_embedder.models.Batch

import scala.io.Source.stdin

trait StdInBatches {
  private val stdInStrings: Iterator[String] = stdin.getLines()

  private def toBatch(jsonString: String) =
    fromJson[Batch](jsonString).get

  protected val batches: Iterator[Batch] =
    stdInStrings.map(toBatch)
}
