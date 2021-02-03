package uk.ac.wellcome.platform.calm_deletion_checker

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.platform.calm_api_client.{
  CalmAkkaHttpClient,
  CalmHttpClient,
  HttpCalmRetriever
}
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
    implicit val materializer: Materializer =
      AkkaBuilder.buildMaterializer()
    implicit val httpClient: CalmHttpClient =
      new CalmAkkaHttpClient()

    implicit val dynamoClient: DynamoDbClient =
      DynamoBuilder.buildDynamoClient(config)
    val dynamoConfig =
      DynamoBuilder.buildDynamoConfig(config, namespace = "vhs")

    new DeletionCheckerWorkerService(
      msgStream = SQSBuilder.buildSQSStream(config),
      messageSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "CALM deletion checker"),
      markDeleted = new DeletionMarker(dynamoConfig.tableName),
      calmRetriever = calmRetriever(config),
      batchSize =
        config.getIntOption("calm.deletion_checker.batch_size").getOrElse(500)
    )
  }

  def calmRetriever(config: Config)(implicit
                                    ec: ExecutionContext,
                                    materializer: Materializer,
                                    httpClient: CalmHttpClient) =
    new HttpCalmRetriever(
      url = config.requireString("calm.api.url"),
      username = config.requireString("calm.api.username"),
      password = config.requireString("calm.api.password"),
    )
}
