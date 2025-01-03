package weco.pipeline.relation_embedder

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.Config
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.SQSBuilder
import weco.typesafe.WellcomeTypesafeApp
import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig {
    config: Config =>
      implicit val actorSystem: ActorSystem =
        ActorSystem("main-actor-system")
      implicit val ec: ExecutionContext =
        actorSystem.dispatcher

      val processor = BatchProcessor(config)

      new RelationEmbedderWorkerService(
        sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
        batchProcessor = processor
      )
  }
}
