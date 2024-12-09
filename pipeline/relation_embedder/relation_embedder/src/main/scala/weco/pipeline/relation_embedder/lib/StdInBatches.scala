package weco.pipeline.relation_embedder.lib

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.scaladsl.{Flow, Source}
import weco.json.JsonUtil._
import weco.pipeline.relation_embedder.models.Batch

import scala.concurrent.Future

trait StdInBatches extends StdInStrings {
  private val toBatchFlow: Flow[String, Batch, NotUsed] =
    Flow.fromFunction((jsonString: String) => fromJson[Batch](jsonString).get)

  protected val batchSource: Source[Batch, Future[IOResult]] = stringSource
    .via(toBatchFlow)

}
