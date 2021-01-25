package uk.ac.wellcome.platform.router

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.elasticsearch.DenormalisedWorkIndexConfig
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Merged}
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

    val esClient = ElasticBuilder.buildElasticClient(config)

    val workIndexer = ElasticIndexerBuilder[Work[Denormalised]](
      config,
      esClient,
      namespace = "denormalised-works",
      indexConfig = DenormalisedWorkIndexConfig
    )

    val workRetriever = ElasticSourceRetrieverBuilder[Work[Merged]](
      config,
      esClient,
      namespace = "merged-works"
    )

    new RouterWorkerService(
      msgStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      indexer = workIndexer,
      config = PipelineStorageStreamBuilder.buildPipelineStorageConfig(config),
messageSender = SNSBuilder
  .buildSNSMessageSender(
    config,
    namespace = "work-sender",
    subject = "Sent from the router"),
      pathsMsgSender = SNSBuilder
        .buildSNSMessageSender(
          config,
          namespace = "path-sender",
          subject = "Sent from the router"),
      workRetriever = workRetriever
    )
  }
}
