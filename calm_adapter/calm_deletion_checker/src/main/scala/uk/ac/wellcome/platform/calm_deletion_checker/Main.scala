package uk.ac.wellcome.platform.calm_deletion_checker

import akka.actor.ActorSystem
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.platform.calm_api_client.AkkaHttpCalmApiClient
import uk.ac.wellcome.storage.typesafe.DynamoBuilder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val ec: ExecutionContext = AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

    implicit val dynamoClient: DynamoDbClient =
      DynamoBuilder.buildDynamoClient(config)
    val dynamoConfig =
      DynamoBuilder.buildDynamoConfig(config, namespace = "vhs")

    new DeletionCheckerWorkerService(
      messageStream = SQSBuilder.buildSQSStream(config),
      messageSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "CALM deletion checker"),
      markDeleted = new DeletionMarker(dynamoConfig.tableName),
      calmApiClient = new AkkaHttpCalmApiClient(
        url = config.requireString("calm.api.url"),
        username = config.requireString("calm.api.username"),
        password = config.requireString("calm.api.password")
      ),
      batchSize =
        config.getIntOption("calm.deletion_checker.batch_size").getOrElse(500)
    )
  }
}
