package uk.ac.wellcome.platform.inference_manager

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.config.Config
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.inference_manager.adapters.FeatureVectorInferrerAdapter
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage
import uk.ac.wellcome.platform.inference_manager.services.{
  ImageDownloader,
  InferenceManagerWorkerService,
  MergedIdentifiedImage
}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val imageDownloader = ImageDownloader(
      config
        .getStringOption("shared.images-root")
        .getOrElse("/tmp")
    )
    val inferrerAdapter = FeatureVectorInferrerAdapter

    val inferrerClientFlow =
      Http()
        .cachedHostConnectionPool[(DownloadedImage, Message)](
          config
            .getStringOption("inferrer.host")
            .getOrElse("localhost"),
          config
            .getIntOption("inferrer.port")
            .getOrElse(80)
        )

    new InferenceManagerWorkerService(
      msgStream = BigMessagingBuilder
        .buildMessageStream[MergedIdentifiedImage](config),
      messageSender = BigMessagingBuilder.buildBigMessageSender(config),
      imageDownloader = imageDownloader,
      inferrerAdapter = inferrerAdapter,
      requestPool = inferrerClientFlow
    )
  }
}
