package weco.pipeline.relation_embedder

import weco.lambda.SQSLambdaApp
import weco.pipeline.relation_embedder.lib._
import weco.json.JsonUtil._
import weco.pipeline.relation_embedder.models.Batch

import scala.concurrent.Future

object LambdaMain
    extends SQSLambdaApp[Batch, String, RelationEmbedderConfig]
    with RelationEmbedderConfigurable {

  private lazy val batchProcessor = BatchProcessor(config)

  def processT(t: List[Batch]): Future[String] = {
    info(s"running relation_embedder lambda, got event: $t")

    Future
      .sequence(t.map(batchProcessor(_)))
      .map(_ => "Done")
  }
}
