package uk.ac.wellcome.platform.ingestor

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.models.work.internal.IdentifiedBaseWork
import uk.ac.wellcome.platform.ingestor.config.builders.IngestorConfigBuilder
import uk.ac.wellcome.platform.ingestor.services.IngestorWorkerService
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    new IngestorWorkerService(
      elasticClient = ElasticBuilder.buildElasticClient(config),
      ingestorConfig = IngestorConfigBuilder.buildIngestorConfig(config),
      messageStream =
        BigMessagingBuilder.buildMessageStream[IdentifiedBaseWork](config)
    )
  }
}
