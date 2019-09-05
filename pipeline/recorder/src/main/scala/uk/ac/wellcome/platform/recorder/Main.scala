package uk.ac.wellcome.platform.recorder

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.services.RecorderWorkerService
import uk.ac.wellcome.models.Implicits._

import uk.ac.wellcome.messaging.typesafe.SNSBuilder
import uk.ac.wellcome.bigmessaging.typesafe.{BigMessagingBuilder, VHSBuilder}

object Main extends WellcomeTypesafeApp {

  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    new RecorderWorkerService(
      store = VHSBuilder.build[TransformedBaseWork](config),
      messageStream =
        BigMessagingBuilder.buildMessageStream[TransformedBaseWork](config),
      msgSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "Sent from the recorder")
    )
  }
}
