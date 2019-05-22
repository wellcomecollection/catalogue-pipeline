package uk.ac.wellcome.platform.matcher

import java.time.Duration

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.typesafe.{MessagingBuilder, SNSBuilder}
import uk.ac.wellcome.models.matcher.MatchedIdentifiers
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.services.MatcherWorkerService
import uk.ac.wellcome.platform.matcher.storage.{DynamoWorkNodeDao, WorkGraphStore}
import uk.ac.wellcome.storage.dynamo._
import uk.ac.wellcome.storage.locking.{DynamoLockDao, DynamoLockDaoConfig, DynamoLockingService}
import uk.ac.wellcome.storage.typesafe.DynamoBuilder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext
import scala.util.Try

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
        dynamoDbClient = dynamoClient,
        dynamoConfig = DynamoBuilder.buildDynamoConfig(config)
      )
    )

    val lockDaoConfig = DynamoLockDaoConfig(
      dynamoConfig =
        DynamoBuilder.buildDynamoConfig(config, namespace = "locking.service"),
      expiryTime = Duration.ofSeconds(180)
    )

    implicit val dynamoLockDao: DynamoLockDao = new DynamoLockDao(
      client = DynamoBuilder.buildDynamoClient(config),
      config = lockDaoConfig
    )

    val lockingService = new DynamoLockingService[Set[MatchedIdentifiers], Try]()

    val workMatcher = new WorkMatcher(
      workGraphStore = workGraphStore,
      lockingService = lockingService
    )

    new MatcherWorkerService(
      messageStream =
        MessagingBuilder.buildMessageStream[TransformedBaseWork](config),
      messageSender = SNSBuilder.buildSNSMessageSender(config, subject = "Sent from the matcher"),
      workMatcher = workMatcher
    )
  }
}
