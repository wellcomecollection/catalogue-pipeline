package uk.ac.wellcome.platform.matcher

import java.time.Duration

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.typesafe.{MessagingBuilder, SNSBuilder}
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.monitoring.typesafe.MetricsBuilder
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.services.MatcherWorkerService
import uk.ac.wellcome.platform.matcher.storage.{WorkGraphStore, DynamoWorkNodeDao}
import uk.ac.wellcome.storage.locking.{
  DynamoLockingService,
  DynamoRowLockDao,
  DynamoRowLockDaoConfig
}
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
      workNodeDao = new DynamoWorkNodeDao(
        dynamoClient = dynamoClient,
        dynamoConfig = DynamoBuilder.buildDynamoConfig(config)
      )
    )

    val rowLockDaoConfig = DynamoRowLockDaoConfig(
      dynamoConfig =
        DynamoBuilder.buildDynamoConfig(config, namespace = "locking.service"),
      duration = Duration.ofSeconds(180)
    )

    val lockingService = new DynamoLockingService(
      lockNamePrefix = "WorkMatcher",
      dynamoRowLockDao = new DynamoRowLockDao(
        dynamoDbClient = dynamoClient,
        rowLockDaoConfig = rowLockDaoConfig
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
