package uk.ac.wellcome.platform.inference_manager

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.config.Config
import software.amazon.awssdk.services.sqs.model.Message

import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.elasticsearch.AugmentedImageIndexConfig
import uk.ac.wellcome.platform.inference_manager.adapters.{
  FeatureVectorInferrerAdapter,
  InferrerAdapter,
  PaletteInferrerAdapter
}
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage
import uk.ac.wellcome.platform.inference_manager.services.{
  ImageDownloader,
  InferenceManagerWorkerService
}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import uk.ac.wellcome.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticSourceRetrieverBuilder,
  PipelineStorageStreamBuilder
}
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.models.work.internal.{Image, ImageState}
import ImageState.{Augmented, Initial}

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

    val esClient = ElasticBuilder.buildElasticClient(config)

    val imageRetriever = ElasticSourceRetrieverBuilder.apply[Image[Initial]](
      config,
      esClient,
      namespace = "initial-images"
    )

    val imageIndexer = ElasticIndexerBuilder[Image[Augmented]](
      config,
      esClient,
      namespace = "augmented-images",
      indexConfig = AugmentedImageIndexConfig
    )

    val pipelineStorageConfig = PipelineStorageStreamBuilder
      .buildPipelineStorageConfig(config)

    new InferenceManagerWorkerService(
      msgStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      msgSender = SNSBuilder.buildSNSMessageSender(
        config,
        subject = "Sent from the inference_manager"),
      imageRetriever = imageRetriever,
      imageIndexer = imageIndexer,
      pipelineStorageConfig = pipelineStorageConfig,
      imageDownloader = imageDownloader,
      inferrerAdapters = Set(
        featureInferrerAdapter,
        paletteInferrerAdapter
      ),
      requestPool = inferrerClientFlow
    )
  }
}
