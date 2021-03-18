package uk.ac.wellcome.platform.merger

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.typesafe.config.Config

import uk.ac.wellcome.models.index.{
  InitialImageIndexConfig,
  MergedWorkIndexConfig
}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.pipeline_storage.EitherIndexer
import uk.ac.wellcome.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticSourceRetrieverBuilder,
  PipelineStorageStreamBuilder
}
import uk.ac.wellcome.platform.merger.services._
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work

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

    val mergerManager = new MergerManager(
      mergerRules = PlatformMerger
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
          indexConfig = MergedWorkIndexConfig
        ),
        ElasticIndexerBuilder[Image[Initial]](
          config,
          esClient,
          namespace = "initial-images",
          indexConfig = InitialImageIndexConfig
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
