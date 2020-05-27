package uk.ac.wellcome.platform.sierra_item_merger

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.VHSWrapper
import uk.ac.wellcome.bigmessaging.typesafe.VHSBuilder
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.platform.sierra_item_merger.services.{
  SierraItemMergerUpdaterService,
  SierraItemMergerWorkerService
}
import uk.ac.wellcome.sierra_adapter.config.builders.SierraVHSBuilder
import uk.ac.wellcome.sierra_adapter.model.{
  SierraItemRecord,
  SierraTransformable
}
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: Materializer =
      AkkaBuilder.buildMaterializer()

    val stransformableStore =
      SierraVHSBuilder.buildSierraVHS[SierraTransformable](config)

    val updaterService = new SierraItemMergerUpdaterService(
      versionedHybridStore = stransformableStore
    )

    new SierraItemMergerWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      sierraItemMergerUpdaterService = updaterService,
      itemRecordStore = new VersionedStore(
        new VHSWrapper(VHSBuilder.build[SierraItemRecord](config))),
      messageSender =
        SNSBuilder.buildSNSMessageSender(config, subject = "Sierra item merger")
    )
  }
}
