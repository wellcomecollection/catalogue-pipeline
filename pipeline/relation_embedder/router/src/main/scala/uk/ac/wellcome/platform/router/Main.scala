package uk.ac.wellcome.platform.router

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.elasticsearch.DenormalisedWorkIndexConfig
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Merged}
import uk.ac.wellcome.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticRetrieverBuilder,
  PipelineStorageStreamBuilder
}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val indexer = ElasticIndexerBuilder.buildIndexer[Work[Denormalised]](
      config,
      namespace = "denormalised-works",
      indexConfig = DenormalisedWorkIndexConfig
    )

    val retriever = ElasticRetrieverBuilder.buildRetriever[Work[Merged]](
      config, namespace = "merged-works"
    )

    val stream = PipelineStorageStreamBuilder.buildPipelineStorageStream(
      SQSBuilder.buildSQSStream[NotificationMessage](config),
      indexer = indexer,
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
      workRetriever = retriever,
      pipelineStream = stream
    )
  }
}
