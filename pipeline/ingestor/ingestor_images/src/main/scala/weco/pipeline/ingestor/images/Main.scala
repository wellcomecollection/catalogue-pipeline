package weco.pipeline.ingestor.images

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.catalogue.internal_model.index.ImagesIndexConfig
import weco.messaging.sns.NotificationMessage
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.{Augmented, Indexed}
import weco.pipeline.ingestor.common.IngestorWorkerService
import weco.pipeline_storage.elastic.ElasticIndexer
import weco.pipeline_storage.typesafe.{
  ElasticSourceRetrieverBuilder,
  PipelineStorageStreamBuilder
}
import weco.typesafe.config.builders.EnrichConfig._

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val msgStream =
      SQSBuilder.buildSQSStream[NotificationMessage](config)

    val client =
      ElasticBuilder
        .buildElasticClient(config, namespace = "pipeline_storage")

    val imageRetriever = ElasticSourceRetrieverBuilder[Image[Augmented]](
      config,
      client = client,
      namespace = "augmented-images")

    val imageIndexer =
      new ElasticIndexer[Image[Indexed]](
        client = client,
        index = Index(config.requireString("es.indexed-images.index")),
        config = ImagesIndexConfig.indexed
      )

    val msgSender = SNSBuilder
      .buildSNSMessageSender(config, subject = "Sent from the ingestor-images")

    val pipelineStream =
      PipelineStorageStreamBuilder.buildPipelineStorageStream(
        msgStream,
        imageIndexer,
        msgSender)(config)

    new IngestorWorkerService(
      pipelineStream = pipelineStream,
      retriever = imageRetriever,
      transform = ImageTransformer.deriveData
    )
  }
}
