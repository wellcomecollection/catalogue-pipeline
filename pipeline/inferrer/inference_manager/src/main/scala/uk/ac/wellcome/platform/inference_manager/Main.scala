package uk.ac.wellcome.platform.inference_manager

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.sqs.model.Message
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.models.work.internal.{AugmentedImage, Identified, MergedImage}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.inference_manager.services.{FeatureVectorInferrerAdapter, InferenceManagerWorkerService}
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.typesafe.S3Builder
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  // This inference manager operates on images
  type Input = MergedImage[Identified]
  type Output = AugmentedImage[Identified]
  val inferrerAdapter = FeatureVectorInferrerAdapter

  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()
    implicit val s3Client: AmazonS3 = S3Builder.buildS3Client(config)

    implicit val msgStore = S3TypedStore[Output]

    val inferrerClientFlow =
      Http()
        .cachedHostConnectionPool[(Message, Input)](
          config.getOrElse[String]("inferrer.host")("localhost"),
          config.getOrElse[Int]("inferrer.port")(80)
        )

    new InferenceManagerWorkerService(
      msgStream = BigMessagingBuilder.buildMessageStream[Input](config),
      msgSender = BigMessagingBuilder.buildBigMessageSender[Output](config),
      inferrerAdapter = inferrerAdapter,
      inferrerClientFlow = inferrerClientFlow
    )
  }
}
