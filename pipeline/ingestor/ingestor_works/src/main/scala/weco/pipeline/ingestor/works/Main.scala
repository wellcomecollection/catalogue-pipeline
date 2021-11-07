package weco.pipeline.ingestor.works

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import weco.typesafe.WellcomeTypesafeApp
import weco.pipeline_storage.Indexable.workIndexable
import weco.typesafe.config.builders.AkkaBuilder
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.typesafe.SNSBuilder
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Indexed}
import weco.pipeline.ingestor.common.IngestorWorkerService
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.typesafe.config.builders.EnrichConfig._

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val client =
      ElasticBuilder
        .buildElasticClient(config, namespace = "pipeline_storage")

    val workRetriever =
      new ElasticSourceRetriever[Work[Denormalised]](
        client = client,
        index = Index(config.requireString("es.denormalised-works.index"))
      )

    val workIndexer =
      new ElasticIndexer[Work[Indexed]](
        client = client,
        index = Index(config.requireString("es.indexed-works.index")),
        config =
          WorksIndexConfig.indexed.withRefreshIntervalFromConfig(config)
      )

    val messageSender = SNSBuilder
      .buildSNSMessageSender(config, subject = "Sent from the ingestor-works")

    val pipelineStream =
      PipelineStorageStreamBuilder
        .buildPipelineStorageStream(workIndexer, messageSender)(config)

    new IngestorWorkerService(
      pipelineStream = pipelineStream,
      retriever = workRetriever,
      transform = WorkTransformer.deriveData
    )
  }
}
