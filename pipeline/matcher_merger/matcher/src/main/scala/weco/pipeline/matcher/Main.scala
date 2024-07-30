package weco.pipeline.matcher

import org.apache.pekko.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.pipeline.matcher.matcher.WorkMatcher
import weco.pipeline.matcher.models.MatcherResult
import weco.pipeline.matcher.services.MatcherWorkerService
import weco.pipeline.matcher.storage.elastic.ElasticWorkStubRetriever
import weco.pipeline.matcher.storage.{WorkGraphStore, WorkNodeDao}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.storage.typesafe.{DynamoBuilder, LockingBuilder}
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object Main extends WellcomeTypesafeApp {
  runWithConfig {
    config: Config =>
      implicit val actorSystem: ActorSystem =
        ActorSystem("main-actor-system")
      implicit val executionContext: ExecutionContext =
        actorSystem.dispatcher

      val workGraphStore = new WorkGraphStore(
        workNodeDao = new WorkNodeDao(
          dynamoClient = DynamoDbClient.builder().build(),
          dynamoConfig = DynamoBuilder.buildDynamoConfig(config)
        )
      )

      val lockingService =
        LockingBuilder
          .buildDynamoLockingService[MatcherResult, Future](
            config,
            namespace = "locking"
          )

      val workMatcher = new WorkMatcher(workGraphStore, lockingService)

      val retriever =
        new ElasticWorkStubRetriever(
          client = ElasticBuilder.buildElasticClient(config),
          index = Index(config.requireString("es.index"))
        )

      new MatcherWorkerService(
        PipelineStorageStreamBuilder.buildPipelineStorageConfig(config),
        retriever = retriever,
        msgStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
        msgSender = SNSBuilder
          .buildSNSMessageSender(config, subject = "Sent from the matcher"),
        workMatcher = workMatcher
      )
  }
}
