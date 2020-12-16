package uk.ac.wellcome.platform.ingestor.images

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.elasticsearch.ImagesIndexConfig
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.{Image, ImageState}
import uk.ac.wellcome.pipeline_storage.Indexable.imageIndexable
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.pipeline_storage.typesafe.ElasticIndexerBuilder
import uk.ac.wellcome.platform.ingestor.common.models.IngestorConfig
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.duration._

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val esClient = ElasticBuilder.buildElasticClient(config)

    val imageIndexer = ElasticIndexerBuilder[Image[ImageState.Indexed]](
      config,
      esClient,
      indexConfig = ImagesIndexConfig
    )

    val ingestorConfig = IngestorConfig(
      batchSize = config.requireInt("es.ingest.batchSize"),
      // TODO: Work out how to get a Duration from a Typesafe flag.
      flushInterval = 1 minute
    )

    new ImageIngestorWorkerService(
      ingestorConfig = ingestorConfig,
      documentIndexer = imageIndexer,
      messageStream = BigMessagingBuilder
        .buildMessageStream[Image[ImageState.Augmented]](config),
      transformBeforeIndex = ImageTransformer.deriveData
    )
  }
}
