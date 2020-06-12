package uk.ac.wellcome.platform.sierra_bib_merger

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.platform.sierra_bib_merger.services.{
  SierraBibMergerUpdaterService,
  SierraBibMergerWorkerService
}
import uk.ac.wellcome.sierra_adapter.config.builders.SierraVHSBuilder
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.sierra_adapter.model.Implicits._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: Materializer =
      AkkaBuilder.buildMaterializer()

    val versionedHybridStore =
      SierraVHSBuilder.buildSierraVHS[SierraTransformable](config)

    val updaterService = new SierraBibMergerUpdaterService(
      versionedHybridStore = versionedHybridStore
    )

    new SierraBibMergerWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      messageSender =
        SNSBuilder.buildSNSMessageSender(config, subject = "Sierra bib merger"),
      sierraBibMergerUpdaterService = updaterService
    )
  }
}
