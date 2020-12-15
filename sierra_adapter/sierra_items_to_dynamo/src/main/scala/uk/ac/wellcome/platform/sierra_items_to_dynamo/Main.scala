package uk.ac.wellcome.platform.sierra_items_to_dynamo

import akka.actor.ActorSystem
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.typesafe.config.Config
import org.scanamo.auto._
import org.scanamo.time.JavaTimeFormats._
import uk.ac.wellcome.platform.sierra_items_to_dynamo.dynamo.Implicits._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.platform.sierra_items_to_dynamo.models.SierraItemLink
import uk.ac.wellcome.platform.sierra_items_to_dynamo.services.{
  SierraItemLinkStore,
  SierraItemsToDynamoWorkerService
}
import uk.ac.wellcome.sierra_adapter.model.SierraItemNumber
import uk.ac.wellcome.storage.store.dynamo.DynamoSingleVersionStore
import uk.ac.wellcome.storage.typesafe.DynamoBuilder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    implicit val dynamoClient: AmazonDynamoDB =
      DynamoBuilder.buildDynamoClient(config)

    val versionedStore = new DynamoSingleVersionStore[SierraItemNumber, SierraItemLink](
      config = DynamoBuilder.buildDynamoConfig(config)
    )

    new SierraItemsToDynamoWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      itemLinkStore = new SierraItemLinkStore(versionedStore),
      messageSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "Sierra Items to Dynamo")
    )
  }
}
