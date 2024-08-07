package weco.pipeline.calm_deletion_checker

import org.apache.pekko.actor.ActorSystem
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.storage.typesafe.DynamoBuilder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.EnrichConfig._
import weco.pipeline.calm_api_client.PekkoHttpCalmApiClient

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig {
    config =>
      implicit val actorSystem: ActorSystem =
        ActorSystem("main-actor-system")
      implicit val ec: ExecutionContext =
        actorSystem.dispatcher

      implicit val dynamoClient: DynamoDbClient =
        DynamoDbClient.builder().build()
      val dynamoConfig =
        DynamoBuilder.buildDynamoConfig(config, namespace = "vhs")

      new DeletionCheckerWorkerService(
        messageStream = SQSBuilder.buildSQSStream(config),
        messageSender = SNSBuilder
          .buildSNSMessageSender(config, subject = "CALM deletion checker"),
        markDeleted = new DeletionMarker(dynamoConfig.tableName),
        calmApiClient = new PekkoHttpCalmApiClient(
          url = config.requireString("calm.api.url"),
          username = config.requireString("calm.api.username"),
          password = config.requireString("calm.api.password")
        ),
        batchSize =
          config.getIntOption("calm.deletion_checker.batch_size").getOrElse(500)
      )
  }
}
