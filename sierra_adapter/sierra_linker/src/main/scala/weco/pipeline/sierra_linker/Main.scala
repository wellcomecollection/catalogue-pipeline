package weco.pipeline.sierra_linker

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.scanamo.DynamoFormat
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import weco.catalogue.source_model.config.SierraRecordTypeBuilder
import weco.catalogue.source_model.sierra.{
  AbstractSierraRecord,
  SierraHoldingsRecord,
  SierraItemRecord,
  SierraOrderRecord
}
import weco.json.JsonUtil._
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.pipeline.sierra_linker.dynamo.Implicits._
import weco.pipeline.sierra_linker.models.{Link, LinkOps}
import weco.pipeline.sierra_linker.services.{LinkStore, SierraLinkerWorker}
import weco.sierra.models.identifiers.{
  SierraHoldingsNumber,
  SierraItemNumber,
  SierraOrderNumber,
  SierraRecordTypes,
  TypedSierraRecordNumber
}
import weco.storage.store.dynamo.DynamoSingleVersionStore
import weco.storage.typesafe.DynamoBuilder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config)

    val messageSender =
      SNSBuilder.buildSNSMessageSender(config, subject = "Sierra linker")

    val recordType = SierraRecordTypeBuilder.build(config, name = "linker")

    import weco.pipeline.sierra_linker.models.LinkOps._

    recordType match {
      case SierraRecordTypes.items =>
        new SierraLinkerWorker(
          sqsStream = sqsStream,
          linkStore =
            createLinkStore[SierraItemNumber, SierraItemRecord](config),
          messageSender = messageSender
        )

      case SierraRecordTypes.holdings =>
        new SierraLinkerWorker(
          sqsStream = sqsStream,
          linkStore =
            createLinkStore[SierraHoldingsNumber, SierraHoldingsRecord](config),
          messageSender = messageSender
        )

      case SierraRecordTypes.orders =>
        new SierraLinkerWorker(
          sqsStream = sqsStream,
          linkStore =
            createLinkStore[SierraOrderNumber, SierraOrderRecord](config),
          messageSender = messageSender
        )

      case other =>
        throw new IllegalArgumentException(
          s"$other is not a resource that can be linked"
        )
    }
  }

  def createLinkStore[Id <: TypedSierraRecordNumber,
                      Record <: AbstractSierraRecord[Id]](config: Config)(
    implicit
    linkOps: LinkOps[Record],
    format: DynamoFormat[Id]
  ): LinkStore[Id, Record] = {
    implicit val dynamoClient: DynamoDbClient =
      DynamoBuilder.buildDynamoClient

    val versionedStore =
      new DynamoSingleVersionStore[Id, Link](
        config = DynamoBuilder.buildDynamoConfig(config)
      )

    new LinkStore[Id, Record](versionedStore)
  }
}
