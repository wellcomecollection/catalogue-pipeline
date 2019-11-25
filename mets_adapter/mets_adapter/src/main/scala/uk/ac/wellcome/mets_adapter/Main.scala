package uk.ac.wellcome.mets_adapter

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.mets_adapter.services.{
  BagRetriever,
  HttpBagRetriever,
  MetsAdapterWorkerService,
  MetsStore,
  TokenService,
}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.bigmessaging.typesafe.VHSBuilder

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()
    implicit val s3Client =
      S3Builder.buildS3Client(config)

    new MetsAdapterWorkerService(
      SQSBuilder.buildSQSStream(config),
      SNSBuilder.buildSNSMessageSender(config, subject = "METS adapter"),
      buildBagRetriever(config),
      S3TypedStore[String],
      MetsStore(VHSBuilder.build(config))
    )
  }

  private def buildBagRetriever(config: Config)(
    implicit
    actorSystem: ActorSystem,
    materializer: ActorMaterializer,
    ec: ExecutionContext): BagRetriever =
    new HttpBagRetriever(
      config.required[String]("bags.api.url"),
      new TokenService(
        config.required[String]("bags.oauth.url"),
        config.required[String]("bags.oauth.client_id"),
        config.required[String]("bags.oauth.secret"),
        config.required[String]("bags.api.url"),
        20 minutes
      )
    )
}
