package weco.catalogue.sierra_linker

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.scanamo.DynamoFormat
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.sierra_adapter.model.{SierraHoldingsNumber, SierraItemNumber, SierraRecordTypes}
import uk.ac.wellcome.storage.store.dynamo.DynamoSingleVersionStore
import uk.ac.wellcome.storage.typesafe.DynamoBuilder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import weco.catalogue.sierra_linker.holdings.HoldingsLinkingRecordStore
import weco.catalogue.sierra_linker.items.ItemLinkingRecordStore

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config)
    val messageSender = SNSBuilder.buildSNSMessageSender(config, subject = "Sierra linker")

    config.requireString("reader.resourceType") match {
      case s: String if s == SierraRecordTypes.items.toString =>
        val versionedStore = createVersionedStore[SierraItemNumber](config)
        val linkStore = new ItemLinkingRecordStore(versionedStore)
        new SierraLinkerWorkerService(sqsStream, linkStore, messageSender)

      case s: String if s == SierraRecordTypes.holdings.toString =>
        val versionedStore = createVersionedStore[SierraHoldingsNumber](config)
        val linkStore = new HoldingsLinkingRecordStore(versionedStore)
        new SierraLinkerWorkerService(sqsStream, linkStore, messageSender)

      case s: String =>
        throw new IllegalArgumentException(
          s"$s is not a Sierra resource type that you can link")
    }
  }

  def createVersionedStore[Id](config: Config)(implicit format: DynamoFormat[Id]): DynamoSingleVersionStore[Id, LinkingRecord] = {
    implicit val dynamoClient: DynamoDbClient =
      DynamoBuilder.buildDynamoClient(config)

    new DynamoSingleVersionStore[Id, LinkingRecord](
      config = DynamoBuilder.buildDynamoConfig(config)
    )
  }
}
