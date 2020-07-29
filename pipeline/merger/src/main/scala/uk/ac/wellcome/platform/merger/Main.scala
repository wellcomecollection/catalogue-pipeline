package uk.ac.wellcome.platform.merger

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.{BigMessagingBuilder, VHSBuilder}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.merger.services._
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val playbackService = new RecorderPlaybackService(
      vhs = VHSBuilder.build[TransformedBaseWork](config)
    )
    val mergerManager = new MergerManager(
      mergerRules = PlatformMerger
    )
    val workSender =
      BigMessagingBuilder.buildBigMessageSender(
        config.getConfig("work-sender").withFallback(config)
      )
    val imageSender =
      BigMessagingBuilder
        .buildBigMessageSender(
          config.getConfig("image-sender").withFallback(config)
        )

    new MergerWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      playbackService = playbackService,
      mergerManager = mergerManager,
      workSender = workSender,
      imageSender = imageSender
    )
  }
}
