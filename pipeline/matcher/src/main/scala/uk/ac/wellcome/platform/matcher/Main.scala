package uk.ac.wellcome.platform.matcher

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.scanamo.generic.auto._
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.models.matcher.MatchedIdentifiers
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.services.MatcherWorkerService
import uk.ac.wellcome.platform.matcher.storage.{WorkGraphStore, WorkNodeDao}
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, LockingBuilder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import com.sksamuel.elastic4s.Index
import uk.ac.wellcome.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import uk.ac.wellcome.platform.matcher.storage.elastic.ElasticWorkLinksRetriever

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val dynamoClient = DynamoBuilder.buildDynamoClient(config)

    val workGraphStore = new WorkGraphStore(
      workNodeDao = new WorkNodeDao(
        dynamoClient,
        DynamoBuilder.buildDynamoConfig(config)
      )
    )

    val lockingService =
      LockingBuilder
        .buildDynamoLockingService[Set[MatchedIdentifiers], Future](config)

    val esClient = ElasticBuilder.buildElasticClient(config)

    val workMatcher = new WorkMatcher(workGraphStore, lockingService)

    val workLinksRetriever =
      new ElasticWorkLinksRetriever(
        esClient,
        index = Index(config.requireString("es.index")))

    new MatcherWorkerService(
      PipelineStorageStreamBuilder.buildPipelineStorageConfig(config),
      workLinksRetriever = workLinksRetriever,
      msgStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      msgSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "Sent from the matcher"),
      workMatcher = workMatcher
    )
  }
}
