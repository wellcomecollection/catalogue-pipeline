package uk.ac.wellcome.platform.merger

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{BigMessagingBuilder, SQSBuilder}
import uk.ac.wellcome.models.work.internal.{BaseWork, TransformedBaseWork}
import uk.ac.wellcome.platform.merger.services._
import uk.ac.wellcome.storage.typesafe.VHSBuilder
import uk.ac.wellcome.storage.vhs.EmptyMetadata
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    val playbackService = new RecorderPlaybackService(
      vhs =
        VHSBuilder.buildVHS[String, TransformedBaseWork, EmptyMetadata](config)
    )

    val mergerManager = new MergerManager(
      mergerRules = PlatformMerger
    )

    new MergerWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      playbackService = playbackService,
      mergerManager = mergerManager,
      messageSender = BigMessagingBuilder.buildBigMessageSender[BaseWork](config)
    )
  }
}
