package weco.pipeline.merger

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.typesafe.config.Config
import weco.catalogue.internal_model.index.{ImagesIndexConfig, WorksIndexConfig}
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.catalogue.internal_model.Implicits._
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.pipeline.merger.services.{IdentifiedWorkLookup, MergerManager, MergerWorkerService, DefaultPlatformMerger, TeiPlatformMerger}
import weco.pipeline_storage.EitherIndexer
import weco.pipeline_storage.typesafe.{ElasticIndexerBuilder, ElasticSourceRetrieverBuilder, PipelineStorageStreamBuilder}
import weco.typesafe.config.builders.EnrichConfig._

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val esClient = ElasticBuilder.buildElasticClient(config)

    val sourceWorkLookup = new IdentifiedWorkLookup(
      retriever = ElasticSourceRetrieverBuilder.apply[Work[Identified]](
        config,
        esClient,
        namespace = "identified-works"
      )
    )
    val mergerMode = config.getStringOption("merger.rules.mode").getOrElse("default")
    val mergerRules = if (mergerMode == "tei") TeiPlatformMerger else DefaultPlatformMerger
    val mergerManager = new MergerManager(
      mergerRules = mergerRules
    )

    val workMsgSender =
      SNSBuilder.buildSNSMessageSender(
        config,
        namespace = "work-sender",
        subject = "Sent by the merger"
      )

    val imageMsgSender =
      SNSBuilder.buildSNSMessageSender(
        config,
        namespace = "image-sender",
        subject = "Sent by the merger"
      )

    val workOrImageIndexer =
      new EitherIndexer[Work[Merged], Image[Initial]](
        ElasticIndexerBuilder[Work[Merged]](
          config,
          esClient,
          namespace = "merged-works",
          indexConfig = WorksIndexConfig.merged
        ),
        ElasticIndexerBuilder[Image[Initial]](
          config,
          esClient,
          namespace = "initial-images",
          indexConfig = ImagesIndexConfig.initial
        )
      )

    new MergerWorkerService(
      msgStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      sourceWorkLookup = sourceWorkLookup,
      mergerManager = mergerManager,
      workOrImageIndexer = workOrImageIndexer,
      workMsgSender = workMsgSender,
      imageMsgSender = imageMsgSender,
      config = PipelineStorageStreamBuilder.buildPipelineStorageConfig(config),
    )
  }
}
