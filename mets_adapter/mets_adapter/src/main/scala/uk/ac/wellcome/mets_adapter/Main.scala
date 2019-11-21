package uk.ac.wellcome.mets_adapter

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.mets_adapter.services.{
  BagRetriever,
  HttpBagRetriever,
  MetsAdapterWorkerService,
  MetsStore,
  TokenService,
}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.bigmessaging.typesafe.VHSBuilder

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    new MetsAdapterWorkerService(
      SQSBuilder.buildSQSStream(config),
      SNSBuilder.buildSNSMessageSender(config, subject = ???),
      buildBagRetriever(config),
      buildXmlStore(config),
      MetsStore(VHSBuilder.build(config))
    )
  }

  private def buildBagRetriever(config: Config)(
    implicit
    actorSystem: ActorSystem,
    materializer: ActorMaterializer,
    ec: ExecutionContext): BagRetriever =
    new HttpBagRetriever(
      ???,
      buildTokenService(config)
    )

  private def buildXmlStore(config: Config): S3TypedStore[String] = ???

  private def buildTokenService(config: Config): TokenService =
    throw new NotImplementedError
}
