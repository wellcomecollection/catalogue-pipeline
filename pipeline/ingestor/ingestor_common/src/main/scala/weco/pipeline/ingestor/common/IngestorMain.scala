package weco.pipeline.ingestor.common

import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import io.circe.{Decoder, Encoder}
import weco.elasticsearch.IndexConfig
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.typesafe.SNSBuilder
import weco.pipeline_storage.Indexable
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.typesafe.config.builders.AkkaBuilder
import weco.typesafe.config.builders.EnrichConfig._
import weco.typesafe.Runnable

import scala.concurrent.ExecutionContext

class IngestorMain[In, Out](
  name: String,
  inputIndexField: String,
  outputIndexField: String,
  indexConfig: IndexConfig,
  transform: In => Out
)(
  implicit
  decoder: Decoder[In],
  encoder: Encoder[Out],
  indexable: Indexable[Out]
) {
  def run(config: Config): Runnable = {
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val client = ElasticBuilder.buildElasticClient(config)

    val retriever =
      new ElasticSourceRetriever[In](
        client = client,
        index = Index(config.requireString(inputIndexField))
      )

    val indexer =
      new ElasticIndexer[Out](
        client = client,
        index = Index(config.requireString(outputIndexField)),
        config = indexConfig.withRefreshIntervalFromConfig(config)
      )

    val messageSender = SNSBuilder
      .buildSNSMessageSender(config, subject = s"Sent from the ingestor-$name")

    val pipelineStream =
      PipelineStorageStreamBuilder
        .buildPipelineStorageStream(indexer, messageSender)(config)

    new IngestorWorkerService(
      pipelineStream = pipelineStream,
      retriever = retriever,
      transform = transform
    )
  }
}
