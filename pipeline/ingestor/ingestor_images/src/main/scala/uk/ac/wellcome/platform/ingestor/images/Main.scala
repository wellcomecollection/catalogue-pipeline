package uk.ac.wellcome.platform.ingestor.images

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticRetrieverBuilder,
  PipelineStorageStreamBuilder
}
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.elasticsearch.IndexedImageIndexConfig
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal._
import ImageState.{Augmented, Indexed}

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val msgStream =
      SQSBuilder.buildSQSStream[NotificationMessage](config)

    val imageRetriever = ElasticRetrieverBuilder[Image[Augmented]](
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

    val pipelineStream =
      PipelineStorageStreamBuilder.buildPipelineStorageStream(
        msgStream,
        imageIndexer,
        msgSender)(config)

    new ImageIngestorWorkerService(
      pipelineStream = pipelineStream,
      imageRetriever = imageRetriever,
      transform = ImageTransformer.deriveData
    )
  }
}
