package uk.ac.wellcome.platform.merger

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.elasticsearch.MergedWorkIndexConfig
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.WorkState.{Merged, Source}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.{ElasticIndexer, ElasticRetriever}
import uk.ac.wellcome.platform.merger.services._
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val workSender =
      SNSBuilder.buildSNSMessageSender(
        config,
        namespace = "work-sender",
        subject = "Sent by the merger"
      )
    val imageSender =
      BigMessagingBuilder
        .buildBigMessageSender(
          config.getConfig("image-sender").withFallback(config)
        )
    val elasticClient = ElasticBuilder.buildElasticClient(config)

    val workRetriever = new ElasticRetriever[Work[Source]](
      elasticClient,
      index = Index(config.requireString("es.source-works.index"))
    )

    val workIndexer = new ElasticIndexer[Work[Merged]](
      elasticClient,
      index = Index(config.requireString("es.merged-works.index")),
      config = MergedWorkIndexConfig
    )

    new MergerWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      workRetriever = workRetriever,
      mergerRules = PlatformMerger,
      workIndexer = workIndexer,
      workSender = workSender,
      imageSender = imageSender
    )
  }
}
