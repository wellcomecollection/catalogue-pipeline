package weco.catalogue.sierra_linker

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.scanamo.DynamoFormat
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.storage.store.dynamo.DynamoSingleVersionStore
import uk.ac.wellcome.storage.typesafe.DynamoBuilder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import weco.catalogue.sierra_adapter.models.SierraRecordTypes._
import weco.catalogue.sierra_adapter.models.{
  AbstractSierraRecord,
  SierraHoldingsNumber,
  SierraHoldingsRecord,
  SierraItemNumber,
  SierraItemRecord,
  SierraRecordTypes,
  TypedSierraRecordNumber
}
import weco.catalogue.sierra_linker.dynamo.Implicits._
import weco.catalogue.sierra_linker.models.{Link, LinkOps}
import weco.catalogue.sierra_linker.services.{LinkStore, SierraLinkerWorker}

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

    val resourceType = config.requireString("linker.resourceType") match {
      case s: String if s == bibs.toString     => bibs
      case s: String if s == items.toString    => items
      case s: String if s == holdings.toString => holdings
      case s: String =>
        throw new IllegalArgumentException(
          s"$s is not a valid Sierra resource type")
    }

    import LinkOps._

    resourceType match {
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
      DynamoBuilder.buildDynamoClient(config)

    val versionedStore =
      new DynamoSingleVersionStore[Id, Link](
        config = DynamoBuilder.buildDynamoConfig(config)
      )

    new LinkStore[Id, Record](versionedStore)
  }
}
