package uk.ac.wellcome.platform.matcher

import java.time.Duration

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.scanamo.auto._
import org.scanamo.time.JavaTimeFormats._

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.services.MatcherWorkerService
import uk.ac.wellcome.platform.matcher.storage.{WorkGraphStore, WorkNodeDao}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.models.Implicits._

import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.messaging.typesafe.SNSBuilder
import uk.ac.wellcome.storage.locking.dynamo.{
  DynamoLockingService,
  DynamoLockDao,
  DynamoLockDaoConfig
}
import uk.ac.wellcome.storage.typesafe.DynamoBuilder

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

    implicit val lockDao = new DynamoLockDao(
      dynamoClient,
      DynamoLockDaoConfig(
        DynamoBuilder.buildDynamoConfig(config, namespace = "locking.service"),
        Duration.ofSeconds(180)
      )
    )

    val workMatcher = new WorkMatcher(workGraphStore, new DynamoLockingService)

    new MatcherWorkerService(
      msgStream = BigMessagingBuilder
        .buildMessageStream[TransformedBaseWork](config),
      msgSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "Sent from the matcher"),
      workMatcher = workMatcher
    )
  }
}
