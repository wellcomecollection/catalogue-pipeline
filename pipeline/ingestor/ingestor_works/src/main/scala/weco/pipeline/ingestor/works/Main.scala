package weco.pipeline.ingestor.works

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.typesafe.config.Config
import weco.typesafe.WellcomeTypesafeApp
import weco.pipeline_storage.Indexable.workIndexable
import weco.typesafe.config.builders.AkkaBuilder
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.messaging.sns.NotificationMessage
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Indexed}
import weco.pipeline.ingestor.common.IngestorWorkerService
import weco.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticSourceRetrieverBuilder,
  PipelineStorageStreamBuilder
}

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val denormalisedWorkStream =
      SQSBuilder.buildSQSStream[NotificationMessage](config)

    val client =
      ElasticBuilder
        .buildElasticClient(config, namespace = "pipeline_storage")

    val workRetriever = ElasticSourceRetrieverBuilder[Work[Denormalised]](
      config,
      client = client,
      namespace = "denormalised-works")

    val workIndexer = ElasticIndexerBuilder[Work[Indexed]](
      config,
      client = client,
      namespace = "indexed-works",
      indexConfig =
        WorksIndexConfig.ingested.withRefreshIntervalFromConfig(config)
    )
    val messageSender = SNSBuilder
      .buildSNSMessageSender(config, subject = "Sent from the ingestor-works")
    val pipelineStream =
      PipelineStorageStreamBuilder.buildPipelineStorageStream(
        denormalisedWorkStream,
        workIndexer,
        messageSender)(config)

    new IngestorWorkerService(
      pipelineStream = pipelineStream,
      retriever = workRetriever,
      transform = WorkTransformer.deriveData
    )
  }
}
