package uk.ac.wellcome.platform.transformer.calm

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.{BigMessagingBuilder, VHSBuilder}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.{
  AWSClientConfigBuilder,
  AkkaBuilder
}

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp with AWSClientConfigBuilder {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val stream = SQSBuilder.buildSQSStream[NotificationMessage](config)
    val sender = BigMessagingBuilder.buildBigMessageSender(config)
    val store = VHSBuilder.build[CalmRecord](config)

    new CalmTransformerWorker(stream, sender, store)
  }
}
