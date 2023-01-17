package weco.pipeline.calm_deletion_checker

import akka.actor.ActorSystem
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.storage.typesafe.DynamoBuilder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.typesafe.config.builders.EnrichConfig._
import weco.pipeline.calm_api_client.AkkaHttpCalmApiClient

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val ec: ExecutionContext = AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

    implicit val dynamoClient: DynamoDbClient =
      DynamoBuilder.buildDynamoClient
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
