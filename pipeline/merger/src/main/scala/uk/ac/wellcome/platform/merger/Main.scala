package uk.ac.wellcome.platform.merger

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext
import uk.ac.wellcome.models.work.internal.{BaseWork, TransformedBaseWork}
import uk.ac.wellcome.platform.merger.services._
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.bigmessaging.typesafe.{BigMessagingBuilder, VHSBuilder}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.typesafe.S3Builder

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()
    implicit val s3Client =
      S3Builder.buildS3Client(config)
    implicit val msgStore =
      S3TypedStore[BaseWork]

    val playbackService = new RecorderPlaybackService(
      vhs = VHSBuilder.build[TransformedBaseWork](config)
    )

    val mergerManager = new MergerManager(
      mergerRules = PlatformMerger
    )

    new MergerWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      playbackService = playbackService,
      mergerManager = mergerManager,
      workSender = BigMessagingBuilder.buildBigMessageSender[BaseWork](config),
    )
  }
}
