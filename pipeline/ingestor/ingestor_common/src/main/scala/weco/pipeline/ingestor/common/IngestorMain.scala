package weco.pipeline.ingestor.common

import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import io.circe.{Decoder, Encoder}
import weco.elasticsearch.IndexConfig
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.sns.SNSConfig
import weco.messaging.typesafe.SNSBuilder
import weco.pipeline_storage.Indexable
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

trait IngestorMain[In, Out] extends WellcomeTypesafeApp {
  val name: String

  val inputIndexField: String
  val outputIndexField: String

  val indexConfig: IndexConfig

  val transform: In => Out

  def runIngestor(config: Config)(
    implicit
    decoder: Decoder[In],
    encoder: Encoder[Out],
    indexable: Indexable[Out]
  ): IngestorWorkerService[SNSConfig, In, Out] = {
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val client =
      ElasticBuilder
        .buildElasticClient(config, namespace = "pipeline_storage")

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
      .buildSNSMessageSender(config, subject = "Sent from the ingestor-works")

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
