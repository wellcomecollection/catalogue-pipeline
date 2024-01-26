package weco.tei.adapter

import akka.actor.ActorSystem
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.storage.store.dynamo.DynamoSingleVersionStore
import weco.storage.typesafe.DynamoBuilder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.EnrichConfig.RichConfig
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.compat.java8.DurationConverters._
import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object Main extends WellcomeTypesafeApp {
  runWithConfig {
    config =>
      implicit val actorSystem: ActorSystem =
        ActorSystem("main-actor-system")
      implicit val ec: ExecutionContext =
        actorSystem.dispatcher
      implicit val dynamoClient: DynamoDbClient =
        DynamoDbClient.builder().build()

      new TeiAdapterWorkerService(
        messageStream = SQSBuilder.buildSQSStream(config),
        messageSender = SNSBuilder.buildSNSMessageSender(config, subject = "TEI adapter"),
        store = new DynamoSingleVersionStore(
          DynamoBuilder.buildDynamoConfig(config, namespace = "tei")
        ),
        parallelism = config.requireInt("tei.adapter.parallelism"),
        delay = config.getDuration("tei.adapter.delete.delay").toScala
      )
  }
}
