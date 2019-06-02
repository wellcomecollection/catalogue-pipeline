package uk.ac.wellcome.platform.recorder

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.typesafe.{BigMessagingBuilder, SNSBuilder}
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.services.RecorderWorkerService
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.storage.typesafe.VHSBuilder
import uk.ac.wellcome.storage.vhs.EmptyMetadata
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    new RecorderWorkerService(
      versionedHybridStore =
        VHSBuilder.buildVHS[String, TransformedBaseWork, EmptyMetadata](config),
      messageStream =
        BigMessagingBuilder.buildMessageStream[TransformedBaseWork](config),
      messageSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "Sent from the recorder")
    )
  }
}
