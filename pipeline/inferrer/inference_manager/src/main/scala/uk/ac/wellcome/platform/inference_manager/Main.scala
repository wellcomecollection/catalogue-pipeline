package uk.ac.wellcome.platform.inference_manager

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import software.amazon.awssdk.services.sqs.model.Message
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.models.work.internal.{
  AugmentedImage,
  Identified,
  MergedImage,
  Minted
}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.inference_manager.services.{
  FeatureVectorInferrerAdapter,
  InferenceManagerWorkerService
}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  // This inference manager operates on images
  type Input = MergedImage[Identified, Minted]
  type Output = AugmentedImage
  val inferrerAdapter = FeatureVectorInferrerAdapter

  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val inferrerClientFlow =
      Http()
        .cachedHostConnectionPool[(Message, Input)](
          config
            .getStringOption("inferrer.host")
            .getOrElse("localhost"),
          config
            .getIntOption("inferrer.port")
            .getOrElse(80)
        )

    new InferenceManagerWorkerService(
      msgStream = BigMessagingBuilder.buildMessageStream[Input](config),
      messageSender = BigMessagingBuilder.buildBigMessageSender(config),
      inferrerAdapter = inferrerAdapter,
      inferrerClientFlow = inferrerClientFlow
    )
  }
}
