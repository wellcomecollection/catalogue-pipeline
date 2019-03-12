package uk.ac.wellcome.platform.matcher

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.typesafe.{MessagingBuilder, SNSBuilder}
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.monitoring.typesafe.MetricsBuilder
import uk.ac.wellcome.platform.matcher.locking.{
  DynamoLockingService,
  DynamoRowLockDao
}
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.services.MatcherWorkerService
import uk.ac.wellcome.platform.matcher.storage.{WorkGraphStore, WorkNodeDao}
import uk.ac.wellcome.storage.typesafe.DynamoBuilder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    val dynamoClient = DynamoBuilder.buildDynamoClient(config)

    val workGraphStore = new WorkGraphStore(
      workNodeDao = new WorkNodeDao(
        dynamoDbClient = dynamoClient,
        dynamoConfig = DynamoBuilder.buildDynamoConfig(config)
      )
    )

    val lockingService = new DynamoLockingService(
      dynamoRowLockDao = new DynamoRowLockDao(
        dynamoDBClient = dynamoClient,
        dynamoConfig =
          DynamoBuilder.buildDynamoConfig(config, namespace = "locking.service")
      ),
      metricsSender = MetricsBuilder.buildMetricsSender(config)
    )

    val workMatcher = new WorkMatcher(
      workGraphStore = workGraphStore,
      lockingService = lockingService
    )

    new MatcherWorkerService(
      messageStream =
        MessagingBuilder.buildMessageStream[TransformedBaseWork](config),
      snsWriter = SNSBuilder.buildSNSWriter(config),
      workMatcher = workMatcher
    )
  }
}
