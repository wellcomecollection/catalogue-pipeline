package uk.ac.wellcome.platform.inference_manager

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.sqs.model.Message
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.models.work.internal.{AugmentedImage, MergedImage, Minted}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.inference_manager.services.{
  FeatureVectorInferrerAdapter,
  InferenceManagerWorkerService
}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.typesafe.S3Builder
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
    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)

    implicit val msgStore = S3TypedStore[AugmentedImage[Minted]]

    val inferrerClientFlow =
      Http()
        .cachedHostConnectionPool[(Message, MergedImage[Minted])]("localhost")

    new InferenceManagerWorkerService(
      msgStream =
        BigMessagingBuilder.buildMessageStream[MergedImage[Minted]](config),
      msgSender = BigMessagingBuilder
        .buildBigMessageSender[AugmentedImage[Minted]](config),
      inferrerAdapter = FeatureVectorInferrerAdapter,
      inferrerClientFlow = inferrerClientFlow
    )
  }
}
