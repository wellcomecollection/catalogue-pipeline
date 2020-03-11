package uk.ac.wellcome.platform.inference_manager

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.models.work.internal.BaseWork
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.inference_manager.services.InferenceManagerWorkerService
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    new InferenceManagerWorkerService(
      msgStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      msgSender = BigMessagingBuilder.buildBigMessageSender[BaseWork](config)
    )
  }
}
