package weco.pipeline.relation_embedder

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import weco.pipeline.relation_embedder.lib._

import scala.concurrent.Future


object LambdaMain
  extends LambdaApp[SQSEvent, String, RelationEmbedderConfig]
  with RelationEmbedderConfigurable {

  import SQSEventOps._
  private lazy val batchProcessor = BatchProcessor(config)

  def processEvent(event: SQSEvent): Future[String] = {
    info(s"running relation_embedder lambda, got event: $event")

    Future.sequence(event.extractBatches.map(batchProcessor(_))).map(_ => "Done")
  }
}
