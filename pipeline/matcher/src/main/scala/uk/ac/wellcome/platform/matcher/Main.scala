package uk.ac.wellcome.platform.matcher

import java.time.Duration
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.typesafe.{BigMessagingBuilder, SNSBuilder}
import uk.ac.wellcome.models.matcher.MatchedIdentifiers
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.services.MatcherWorkerService
import uk.ac.wellcome.platform.matcher.storage.{
  DynamoWorkNodeDao,
  WorkGraphStore
}
import uk.ac.wellcome.storage.locking._
import uk.ac.wellcome.storage.typesafe.DynamoBuilder
import uk.ac.wellcome.storage.{LockDao, LockingService}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.{ExecutionContext, Future}

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

    val dynamoLockDao = new DynamoLockDao(
      client = dynamoClient,
      config = DynamoLockDaoConfig(
        dynamoConfig = DynamoBuilder
          .buildDynamoConfig(config, namespace = "locking.service"),
        expiryTime = Duration.ofSeconds(30)
      )
    )

    val lockingService = new LockingService[
      Set[MatchedIdentifiers],
      Future,
      LockDao[String, UUID]] {
      override implicit val lockDao: LockDao[String, UUID] = dynamoLockDao

      override protected def createContextId(): lockDao.ContextId =
        UUID.randomUUID()
    }

    val workMatcher = new WorkMatcher(
      workGraphStore = workGraphStore,
      lockingService = lockingService
    )

    new MatcherWorkerService(
      messageStream =
        BigMessagingBuilder.buildMessageStream[TransformedBaseWork](config),
      messageSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "Sent by the matcher"),
      workMatcher = workMatcher
    )
  }
}
