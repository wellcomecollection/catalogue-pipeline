package uk.ac.wellcome.platform.ingestor.images

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.elasticsearch.ImagesIndexConfig
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.AugmentedImage
import uk.ac.wellcome.pipeline_storage.typesafe.ElasticIndexerBuilder
import uk.ac.wellcome.platform.ingestor.common.builders.IngestorConfigBuilder
import uk.ac.wellcome.platform.ingestor.common.services.IngestorWorkerService
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val documentIndexer = ElasticIndexerBuilder.buildIndexer[AugmentedImage](
      config, indexConfig = ImagesIndexConfig
    )

    IngestorWorkerService(
      ingestorConfig = IngestorConfigBuilder.buildIngestorConfig(config),
      documentIndexer = documentIndexer,
      messageStream =
        BigMessagingBuilder.buildMessageStream[AugmentedImage](config)
    )
  }
}
