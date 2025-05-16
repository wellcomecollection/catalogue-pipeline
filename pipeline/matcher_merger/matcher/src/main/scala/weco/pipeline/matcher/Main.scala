package weco.pipeline.matcher

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.Config
import org.scanamo.generic.auto._
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.pipeline.matcher.config.{MatcherConfig, MatcherConfigurable}
import weco.pipeline.matcher.matcher.WorkMatcher
import weco.pipeline.matcher.services.MatcherWorkerService
import weco.pipeline.matcher.storage.elastic.ElasticWorkStubRetriever
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.typesafe.WellcomeTypesafeApp

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object Main extends WellcomeTypesafeApp with MatcherConfigurable {
  runWithConfig {
    rawConfig: Config =>
      implicit val actorSystem: ActorSystem =
        ActorSystem("main-actor-system")
      implicit val executionContext: ExecutionContext =
        actorSystem.dispatcher

      val config: MatcherConfig = build(rawConfig)

      val workMatcher =
        WorkMatcher(config.dynamoConfig, config.dynamoLockDAOConfig)

      val retriever =
        new ElasticWorkStubRetriever(
          client = ElasticBuilder.buildElasticClient(config.elasticConfig),
          index = config.index
        )

      new MatcherWorkerService(
        PipelineStorageStreamBuilder.buildPipelineStorageConfig(rawConfig),
        retriever = retriever,
        msgStream = SQSBuilder.buildSQSStream[NotificationMessage](rawConfig),
        msgSender = SNSBuilder
          .buildSNSMessageSender(rawConfig, subject = "Sent from the matcher"),
        workMatcher = workMatcher
      )
  }
}
