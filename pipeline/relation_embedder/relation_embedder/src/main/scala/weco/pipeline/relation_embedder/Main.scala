package weco.pipeline.relation_embedder

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.Config
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.SQSBuilder
import weco.pipeline.relation_embedder.lib.RelationEmbedderConfigurable
import weco.typesafe.WellcomeTypesafeApp

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp with RelationEmbedderConfigurable {
  runWithConfig {
    config: Config =>
      implicit val actorSystem: ActorSystem =
        ActorSystem("main-actor-system")
      implicit val ec: ExecutionContext =
        actorSystem.dispatcher

      val processor = BatchProcessor(build(config))

      new RelationEmbedderWorkerService(
        sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
        batchProcessor = processor
      )
  }
}
