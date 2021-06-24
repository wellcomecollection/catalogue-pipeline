package weco.tei.adapter

import akka.actor.ActorSystem
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.storage.store.dynamo.DynamoSingleVersionStore
import uk.ac.wellcome.storage.typesafe.DynamoBuilder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig.RichConfig
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.compat.java8.DurationConverters._
import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val ec: ExecutionContext = AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val dynamoClilent: DynamoDbClient =
      DynamoBuilder.buildDynamoClient(config)
    new TeiAdapterWorkerService(
      messageStream = SQSBuilder.buildSQSStream(config),
    messageSender =
      SNSBuilder.buildSNSMessageSender(config, subject = "TEI adapter"),
      store = new DynamoSingleVersionStore(
        DynamoBuilder.buildDynamoConfig(config, namespace = "tei")
      ),
      parallelism = config.requireInt("tei.adapter.parallelism"),
      delay = config.getDuration("tei.adapter.delete.delay").toScala
    )
  }
}
