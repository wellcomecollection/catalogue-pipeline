package weco.pipeline.router

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.catalogue.internal_model.work.Work
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val esClient = ElasticBuilder.buildElasticClient(config)

    val workIndexer =
      new ElasticIndexer[Work[Denormalised]](
        client = esClient,
        index = Index(config.requireString(s"es.denormalised-works.index")),
        config = WorksIndexConfig.denormalised
      )

    val workRetriever =
      new ElasticSourceRetriever[Work[Merged]](
        client = esClient,
        index = Index(config.requireString("es.merged-works.index"))
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
