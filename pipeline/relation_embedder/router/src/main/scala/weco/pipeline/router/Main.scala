package weco.pipeline.router

import akka.actor.ActorSystem
import com.typesafe.config.Config
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import weco.pipeline_storage.typesafe.ElasticSourceRetrieverBuilder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.catalogue.internal_model.work.Work
import weco.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticSourceRetrieverBuilder,
  PipelineStorageStreamBuilder
}

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

    val workSender =
      SNSBuilder
        .buildSNSMessageSender(
          config,
          namespace = "work-sender",
          subject = "Sent from the router")

    val pathSender =
      SNSBuilder
        .buildSNSMessageSender(
          config,
          namespace = "path-sender",
          subject = "Sent from the router")

    val stream = PipelineStorageStreamBuilder.buildPipelineStorageStream(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      indexer = workIndexer,
      messageSender = workSender
    )(config)

    new RouterWorkerService(
      pathsMsgSender = pathSender,
      workRetriever = workRetriever,
      pipelineStream = stream
    )
  }
}
