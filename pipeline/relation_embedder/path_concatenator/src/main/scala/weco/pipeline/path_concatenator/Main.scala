package weco.pipeline.path_concatenator

import org.apache.pekko.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.pipeline_storage.elastic.ElasticIndexer
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig {
    config: Config =>
      implicit val actorSystem: ActorSystem =
        ActorSystem("main-actor-system")
      implicit val ec: ExecutionContext =
        actorSystem.dispatcher

      val esClient = ElasticBuilder.buildElasticClient(config)

      val denormalisedIndex =
        Index(config.requireString("es.denormalised.index"))

      val pathsModifier = PathsModifier(
        pathsService = new PathsService(
          elasticClient = esClient,
          index = denormalisedIndex
        )
      )

      val workIndexer =
        new ElasticIndexer[Work[Merged]](
          client = esClient,
          index = denormalisedIndex
        )

      val downstreamSender =
        SNSBuilder
          .buildSNSMessageSender(
            config,
            namespace = "downstream",
            subject = "Sent from the path concatenator"
          )

      new PathConcatenatorWorkerService(
        sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
        pathsModifier = pathsModifier,
        workIndexer = workIndexer,
        msgSender = downstreamSender
      )
  }
}
