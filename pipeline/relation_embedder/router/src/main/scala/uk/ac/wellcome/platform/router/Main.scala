package uk.ac.wellcome.platform.router

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.models.index.WorksIndexConfig
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.models.Implicits._
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import uk.ac.wellcome.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticSourceRetrieverBuilder,
  PipelineStorageStreamBuilder
}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import weco.catalogue.internal_model.work.Work

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
      indexConfig = WorksIndexConfig.denormalised
    )

    val workRetriever = ElasticSourceRetrieverBuilder[Work[Merged]](
      config,
      esClient,
      namespace = "merged-works"
    )

    val stream = PipelineStorageStreamBuilder.buildPipelineStorageStream(
      SQSBuilder.buildSQSStream[NotificationMessage](config),
      indexer = workIndexer,
      SNSBuilder
        .buildSNSMessageSender(
          config,
          namespace = "work-sender",
          subject = "Sent from the router")
    )(config)

    new RouterWorkerService(
      pathsMsgSender = SNSBuilder
        .buildSNSMessageSender(
          config,
          namespace = "path-sender",
          subject = "Sent from the router"),
      workRetriever = workRetriever,
      pipelineStream = stream
    )
  }
}
