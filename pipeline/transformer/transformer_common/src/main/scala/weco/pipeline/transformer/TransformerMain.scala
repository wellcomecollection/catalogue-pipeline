package weco.pipeline.transformer

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import io.circe.Decoder
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.source_model.SourcePayload
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.typesafe.SNSBuilder
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.storage.typesafe.S3Builder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

trait TransformerMain[Payload <: SourcePayload, SourceData] extends WellcomeTypesafeApp {
  val sourceName: String

  def createTransformer(implicit s3Client: AmazonS3): Transformer[SourceData]
  def createSourceDataRetriever(implicit s3Client: AmazonS3): SourceDataRetriever[Payload, SourceData]

  implicit val decoder: Decoder[Payload]

  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val esClient = ElasticBuilder.buildElasticClient(config)

    val sourceWorkIndex = Index(config.requireString("es.index"))

    val sourceWorkIndexer =
      new ElasticIndexer[Work[Source]](
        client = esClient,
        index = sourceWorkIndex,
        config = WorksIndexConfig.source
      )

    val sourceWorkRetriever =
      new ElasticSourceRetriever[Work[Source]](
        client = esClient,
        index = sourceWorkIndex
      )

    val messageSender =
      SNSBuilder
        .buildSNSMessageSender(
          config,
          subject = s"Sent from the $sourceName transformer")

    val pipelineStream = PipelineStorageStreamBuilder
      .buildPipelineStorageStream(sourceWorkIndexer, messageSender)(config)

    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client

    new TransformerWorker(
      transformer = createTransformer,
      pipelineStream = pipelineStream,
      retriever = sourceWorkRetriever,
      sourceDataRetriever = createSourceDataRetriever
    )
  }
}
