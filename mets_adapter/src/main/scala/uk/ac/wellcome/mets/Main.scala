package uk.ac.wellcome.mets

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import com.amazonaws.services.sns.AmazonSNSAsync

import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.messaging.typesafe.{SQSBuilder, SNSBuilder}
import uk.ac.wellcome.mets.services.{BagRetriever, MetsAdaptorWorkerService, SNSConfig, TokenService}

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    new MetsAdaptorWorkerService(
      SQSBuilder.buildSQSStream(config),
      SNSBuilder.buildSNSMessageSender(config, subject = "?"),
      buildBagRetriever(config),
    )
  }

  private def buildSNSClient(config: Config): AmazonSNSAsync =
    throw new NotImplementedError

  private def buildSNSConfig(config: Config): SNSConfig =
    throw new NotImplementedError

  private def buildBagRetriever(config: Config)(
    implicit
    actorSystem: ActorSystem,
    materializer: ActorMaterializer,
    ec: ExecutionContext): BagRetriever =
    new BagRetriever(
      "URL??",
      buildTokenService(config)
    )

  private def buildTokenService(config: Config): TokenService =
    throw new NotImplementedError
}
