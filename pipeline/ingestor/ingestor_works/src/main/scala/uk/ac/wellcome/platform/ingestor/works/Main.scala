package uk.ac.wellcome.platform.ingestor.works

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.elasticsearch.IndexedWorkIndexConfig
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.Indexable.workIndexable
import uk.ac.wellcome.platform.ingestor.common.builders.IngestorConfigBuilder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import WorkState.{Identified, Indexed}
import uk.ac.wellcome.pipeline_storage.typesafe.ElasticIndexerBuilder

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val identifiedWorkStream =
      BigMessagingBuilder.buildMessageStream[Work[Identified]](config)

    val workIndexer = ElasticIndexerBuilder[Work[Indexed]](
      config,
      indexConfig = IndexedWorkIndexConfig
    )

    new WorkIngestorWorkerService(
      ingestorConfig = IngestorConfigBuilder.buildIngestorConfig(config),
      documentIndexer = workIndexer,
      messageStream = identifiedWorkStream,
      transformBeforeIndex = WorkTransformer.deriveData
    )
  }
}
