package weco.pipeline.router

import org.apache.pekko.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.typesafe.SNSBuilder
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import weco.typesafe.WellcomeTypesafeApp
import weco.catalogue.internal_model.work.Work
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}
import weco.pipeline_storage.typesafe.PipelineStorageStreamBuilder
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig {
    config: Config =>
      implicit val ec: ExecutionContext =
        ActorSystem("main-actor-system").dispatcher

      val esClient = ElasticBuilder.buildElasticClient(config)

      val workIndexer =
        new ElasticIndexer[Work[Denormalised]](
          client = esClient,
          index = Index(config.requireString(s"es.denormalised-works.index"))
        )

      val workRetriever =
        new ElasticSourceRetriever[Work[Merged]](
          client = esClient,
          index = Index(config.requireString("es.merged-works.index"))
        )

      val workSender =
        SNSBuilder
          .buildSNSMessageSender(
            config,
            namespace = "work-sender",
            subject = "Sent from the router"
          )

      val pathSender =
        SNSBuilder
          .buildSNSMessageSender(
            config,
            namespace = "path-sender",
            subject = "Sent from the router"
          )

      val pathConcatenatorSender =
        SNSBuilder
          .buildSNSMessageSender(
            config,
            namespace = "path-concatenator-sender",
            subject = "Sent from the router"
          )

      val pipelineStream =
        PipelineStorageStreamBuilder
          .buildPipelineStorageStream(workIndexer, workSender)(config)

      new RouterWorkerService(
        pathsMsgSender = pathSender,
        pathConcatenatorMsgSender = pathConcatenatorSender,
        workRetriever = workRetriever,
        pipelineStream = pipelineStream
      )
  }
}
