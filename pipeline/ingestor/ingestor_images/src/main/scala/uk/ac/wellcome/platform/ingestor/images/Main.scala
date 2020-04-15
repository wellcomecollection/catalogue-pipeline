package uk.ac.wellcome.platform.ingestor.images

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.elasticsearch.{
  ElasticsearchIndexCreator,
  ImagesIndexConfig
}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.AugmentedImage
import uk.ac.wellcome.platform.ingestor.common.builders.IngestorConfigBuilder
import uk.ac.wellcome.platform.ingestor.common.services.IngestorWorkerService
import uk.ac.wellcome.platform.ingestor.images.services.ImagesIndexer
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    val indexName = config.required[String]("es.index")
    val elasticClient = ElasticBuilder.buildElasticClient(config)
    val index = Index(indexName)
    new IngestorWorkerService(
      ingestorConfig = IngestorConfigBuilder.buildIngestorConfig(config),
      documentIndexer = new ImagesIndexer(elasticClient, index),
      indexCreator =
        new ElasticsearchIndexCreator(elasticClient, index, ImagesIndexConfig),
      messageStream =
        BigMessagingBuilder.buildMessageStream[AugmentedImage](config)
    )
  }
}
