package weco.pipeline.path_concatenator

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.pipeline_storage.elastic.ElasticIndexer
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

    val esClient = ElasticBuilder.buildElasticClient(config)

    val pathsModifier = PathsModifier(
      pathsService = new PathsService(
        elasticClient = esClient,
        index = Index(config.requireString("es.read-from.index"))
      )
    )

    val workIndexer =
      new ElasticIndexer[Work[Merged]](
        client = esClient,
        index = Index(config.requireString(s"es.write-to.index")),
        config = WorksIndexConfig.merged
      )

    val downstreamSender =
      SNSBuilder
        .buildSNSMessageSender(
          config,
          namespace = "downstream",
          subject = "Sent from the path concatenator")


    new PathConcatenatorWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config) ,
      pathsModifier = pathsModifier,
      workIndexer = workIndexer,
      msgSender = downstreamSender
    )
  }
}
