package uk.ac.wellcome.platform.ingestor.images

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.elasticsearch.IndexedImageIndexConfig
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.ImageState.{Augmented, Indexed}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.typesafe.{ElasticIndexerBuilder, ElasticSourceRetrieverBuilder, PipelineStorageStreamBuilder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val msgStream =
      SQSBuilder.buildSQSStream[NotificationMessage](config)

    val imageRetriever = ElasticSourceRetrieverBuilder[Image[Augmented]](
      config,
      ElasticBuilder.buildElasticClient(config, namespace = "pipeline_storage"),
      namespace = "augmented-images")

    val imageIndexer = ElasticIndexerBuilder[Image[Indexed]](
      config,
      ElasticBuilder.buildElasticClient(config, namespace = "catalogue"),
      namespace = "indexed-images",
      indexConfig = IndexedImageIndexConfig
    )
    val msgSender = SNSBuilder
      .buildSNSMessageSender(config, subject = "Sent from the ingestor-images")

    new ImageIngestorWorkerService(
      msgStream,
      imageIndexer,
      PipelineStorageStreamBuilder.buildPipelineStorageConfig(config),
      msgSender,
      imageRetriever = imageRetriever,
      transform = ImageTransformer.deriveData
    )
  }
}
