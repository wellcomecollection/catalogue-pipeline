package uk.ac.wellcome.platform.inference_manager

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.config.Config
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.inference_manager.adapters.{
  FeatureVectorInferrerAdapter,
  InferrerAdapter,
  PaletteInferrerAdapter
}
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

    val featureInferrerAdapter = new FeatureVectorInferrerAdapter(
      config.getString("inferrer.feature.host"),
      config.getInt("inferrer.feature.port")
    )
    val paletteInferrerAdapter = new PaletteInferrerAdapter(
      config.getString("inferrer.palette.host"),
      config.getInt("inferrer.palette.port")
    )

    val inferrerClientFlow =
      Http().superPool[((DownloadedImage, InferrerAdapter), Message)]()

    new InferenceManagerWorkerService(
      msgStream = BigMessagingBuilder
        .buildMessageStream[MergedIdentifiedImage](config),
      messageSender = BigMessagingBuilder.buildBigMessageSender(config),
      imageDownloader = imageDownloader,
      inferrerAdapters = Set(
        featureInferrerAdapter,
        paletteInferrerAdapter
      ),
      requestPool = inferrerClientFlow
    )
  }
}
