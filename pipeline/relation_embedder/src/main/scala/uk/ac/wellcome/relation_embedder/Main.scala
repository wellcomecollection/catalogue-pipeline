package uk.ac.wellcome.relation_embedder

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    new RelationEmbedderWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      msgSender = ???,
      workRetriever = ???,
      relatedWorksService = ???
    )
  }
}
