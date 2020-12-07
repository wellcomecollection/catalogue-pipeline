package uk.ac.wellcome.platform.ingestor.works

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.typesafe.config.Config

import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.pipeline_storage.Indexable.workIndexable
import uk.ac.wellcome.platform.ingestor.common.builders.IngestorConfigBuilder
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticRetrieverBuilder
}
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.elasticsearch.IndexedWorkIndexConfig
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal._
import WorkState.{Denormalised, Indexed}

object Main extends WellcomeTypesafeApp {
  { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val denormalisedWorkStream =
      SQSBuilder.buildSQSStream[NotificationMessage](config)

    val workRetriever = ElasticRetrieverBuilder[Work[Denormalised]](
      config,
      namespace = "denormalised-works")

    val workIndexer = ElasticIndexerBuilder[Work[Indexed]](
      config,
      namespace = "indexed-works",
      indexConfig = IndexedWorkIndexConfig
    )

    new WorkIngestorWorkerService(
      ingestorConfig = IngestorConfigBuilder.buildIngestorConfig(config),
      workRetriever = workRetriever,
      workIndexer = workIndexer,
      msgStream = denormalisedWorkStream,
      transformBeforeIndex = WorkTransformer.deriveData
    )
  }
}
