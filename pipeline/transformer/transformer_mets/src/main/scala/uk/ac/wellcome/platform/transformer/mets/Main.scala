package uk.ac.wellcome.platform.transformer.mets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.typesafe.SNSBuilder
import uk.ac.wellcome.platform.transformer.mets.model.IngestUpdate
import uk.ac.wellcome.platform.transformer.mets.service.MetsTransformerWorkerService
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    new MetsTransformerWorkerService(BigMessagingBuilder.buildMessageStream[IngestUpdate](config), SNSBuilder.buildSNSMessageSender(config, subject = "Sent from the mets transformer"))
  }
}
