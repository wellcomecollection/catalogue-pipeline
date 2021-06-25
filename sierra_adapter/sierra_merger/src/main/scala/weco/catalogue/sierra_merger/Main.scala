package weco.catalogue.sierra_merger

import akka.actor.ActorSystem
import com.typesafe.config.Config
import weco.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import weco.catalogue.sierra_merger.services.{Updater, Worker}
import weco.catalogue.source_model.config.{
  SierraRecordTypeBuilder,
  SourceVHSBuilder
}
import weco.catalogue.source_model.sierra.identifiers.SierraRecordTypes
import weco.catalogue.source_model.sierra.{
  SierraBibRecord,
  SierraHoldingsRecord,
  SierraItemRecord,
  SierraOrderRecord,
  SierraTransformable
}

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config)

    val messageSender =
      SNSBuilder.buildSNSMessageSender(config, subject = "Sierra merger")

    val sourceVHS = SourceVHSBuilder.build[SierraTransformable](config)

    val recordType = SierraRecordTypeBuilder.build(config, name = "merger")

    import weco.catalogue.sierra_merger.models.TransformableOps._
    import weco.catalogue.sierra_merger.models.RecordOps._

    recordType match {
      case SierraRecordTypes.bibs =>
        new Worker(
          sqsStream = sqsStream,
          updater = new Updater[SierraBibRecord](sourceVHS),
          messageSender = messageSender
        )

      case SierraRecordTypes.items =>
        new Worker(
          sqsStream = sqsStream,
          updater = new Updater[SierraItemRecord](sourceVHS),
          messageSender = messageSender
        )

      case SierraRecordTypes.holdings =>
        new Worker(
          sqsStream = sqsStream,
          updater = new Updater[SierraHoldingsRecord](sourceVHS),
          messageSender = messageSender
        )

      case SierraRecordTypes.orders =>
        new Worker(
          sqsStream = sqsStream,
          updater = new Updater[SierraOrderRecord](sourceVHS),
          messageSender = messageSender
        )
    }
  }
}
