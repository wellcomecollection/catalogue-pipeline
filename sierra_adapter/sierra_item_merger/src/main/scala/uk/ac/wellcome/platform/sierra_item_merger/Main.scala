package uk.ac.wellcome.platform.sierra_item_merger

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.SierraTransformable._
import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord
import uk.ac.wellcome.platform.sierra_item_merger.services.{SierraItemMergerUpdaterService, SierraItemMergerWorkerService}
import uk.ac.wellcome.storage.dynamo._
import uk.ac.wellcome.storage.typesafe.{S3Builder, VHSBuilder}
import uk.ac.wellcome.storage.vhs.EmptyMetadata
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    val vhs =
      VHSBuilder.buildVHS[String, SierraTransformable, EmptyMetadata](config)

    val updaterService = new SierraItemMergerUpdaterService(vhs)

    new SierraItemMergerWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      sierraItemMergerUpdaterService = updaterService,
      itemStore = S3Builder.buildObjectStore[SierraItemRecord](config),
      messageSender = SNSBuilder.buildSNSMessageSender(config, subject = "Sent from the sierra item merger")
    )
  }
}
