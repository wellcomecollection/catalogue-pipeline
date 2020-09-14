package uk.ac.wellcome.platform.recorder

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.recorder.services.RecorderWorkerService
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.messaging.typesafe.SNSBuilder
import uk.ac.wellcome.bigmessaging.typesafe.{BigMessagingBuilder, VHSBuilder}
import WorkState.Unidentified

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

    new RecorderWorkerService(
      store = VHSBuilder.build[Work[Unidentified]](config),
      messageStream =
        BigMessagingBuilder.buildMessageStream[Work[Unidentified]](config),
      msgSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "Sent from the recorder")
    )
  }
}
