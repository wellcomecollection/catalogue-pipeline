package weco.pipeline.merger

import org.apache.pekko.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.Initial
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{
  Denormalised,
  Identified,
  Merged
}
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.pipeline.merger.services.{
  IdentifiedWorkLookup,
  MergerManager,
  MergerWorkerService,
  PlatformMerger,
  WorkRouter
}
import weco.pipeline_storage.EitherIndexer
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
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

      val sourceWorkLookup = new IdentifiedWorkLookup(retriever)

      val mergerManager = new MergerManager(PlatformMerger)

      val workSender =
        SNSBuilder.buildSNSMessageSender(
          config,
          namespace = "work-sender",
          subject = "Sent by the merger"
        )

      val pathSender =
        SNSBuilder
          .buildSNSMessageSender(
            config,
            namespace = "path-sender",
            subject = "Sent by the merger"
          )

      val pathConcatenatorSender =
        SNSBuilder
          .buildSNSMessageSender(
            config,
            namespace = "path-concatenator-sender",
            subject = "Sent by the merger"
          )

      val workRouter = new WorkRouter(
        workSender = workSender,
        pathSender = pathSender,
        pathConcatenatorSender = pathConcatenatorSender
      )

      val imageMsgSender =
        SNSBuilder.buildSNSMessageSender(
          config,
          namespace = "image-sender",
          subject = "Sent by the merger"
        )

      val workOrImageIndexer =
        new EitherIndexer[Either[Work[Merged], Work[Denormalised]], Image[
          Initial
        ]](
          leftIndexer = new EitherIndexer[Work[Merged], Work[Denormalised]](
            leftIndexer = new ElasticIndexer[Work[Merged]](
              client = esClient,
              index = Index(config.requireString("es.denormalised-works.index"))
            ),
            rightIndexer = new ElasticIndexer[Work[Denormalised]](
              client = esClient,
              index = Index(config.requireString("es.denormalised-works.index"))
            )
          ),
          rightIndexer = new ElasticIndexer[Image[Initial]](
            client = esClient,
            index = Index(config.requireString("es.initial-images.index"))
          )
        )

      new MergerWorkerService(
        msgStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
        sourceWorkLookup = sourceWorkLookup,
        mergerManager = mergerManager,
        workOrImageIndexer = workOrImageIndexer,
        workRouter = workRouter,
        imageMsgSender = imageMsgSender,
        config = PipelineStorageStreamBuilder.buildPipelineStorageConfig(config)
      )
  }
}
