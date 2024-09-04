package weco.pipeline.merger

import org.apache.pekko.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Identified, Merged}
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.pipeline.merger.services.{
  CommandLineMergerWorkerService,
  IdentifiedWorkLookup,
  MergerManager,
  MergerWorkerService,
  PlatformMerger
}
import weco.pipeline_storage.EitherIndexer
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {

  // read and print args passed from the command line
  val runAsCli = args.length > 0
  val idsToCheck = if (runAsCli) Some(args(0)) else None

  runWithConfig {
    config: Config =>
      implicit val actorSystem: ActorSystem =
        ActorSystem("main-actor-system")
      implicit val executionContext: ExecutionContext =
        actorSystem.dispatcher

      val esClient = ElasticBuilder.buildElasticClient(config)

      val retriever =
        new ElasticSourceRetriever[Work[Identified]](
          client = esClient,
          index = Index(config.requireString("es.identified-works.index"))
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
          leftIndexer = new ElasticIndexer[Work[Merged]](
            client = esClient,
            index = Index(config.requireString("es.merged-works.index"))
          ),
          rightIndexer = new ElasticIndexer[Image[Initial]](
            client = esClient,
            index = Index(config.requireString("es.initial-images.index"))
          )
        )

      val mergerManager = new MergerManager(PlatformMerger)

      if (runAsCli) {
        val sourceWorkLookup = new IdentifiedWorkLookup(
          retriever = retriever,
          checkLatestVersion = false
        )

        new CommandLineMergerWorkerService(
          sourceWorkLookup = sourceWorkLookup,
          mergerManager = mergerManager
        )(idsToCheck)
      } else {
        val sourceWorkLookup = new IdentifiedWorkLookup(retriever)

        new MergerWorkerService(
          msgStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
          sourceWorkLookup = sourceWorkLookup,
          mergerManager = mergerManager,
          workOrImageIndexer = workOrImageIndexer,
          workMsgSender = workMsgSender,
          imageMsgSender = imageMsgSender,
          config =
            PipelineStorageStreamBuilder.buildPipelineStorageConfig(config)
        )
      }
  }
}
