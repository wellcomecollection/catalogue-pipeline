package uk.ac.wellcome.platform.merger

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.{BigMessagingBuilder, VHSBuilder}
import uk.ac.wellcome.elasticsearch.MergedWorkIndexConfig
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.services._
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import WorkState.{Merged, Source}
import uk.ac.wellcome.pipeline_storage.typesafe.ElasticIndexerBuilder

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val playbackService = new RecorderPlaybackService(
      vhs = VHSBuilder.build[Work[Source]](config)
    )
    val mergerManager = new MergerManager(
      mergerRules = PlatformMerger
    )
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

    val workIndexer = ElasticIndexerBuilder.buildIndexer[Work[Merged]](
      config,
      indexConfig = MergedWorkIndexConfig
    )

    new MergerWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      playbackService = playbackService,
      mergerManager = mergerManager,
      workIndexer = workIndexer,
      workSender = workSender,
      imageSender = imageSender
    )
  }
}
