package uk.ac.wellcome.relation_embedder

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.AkkaBuilder
import weco.typesafe.config.builders.EnrichConfig._
import weco.catalogue.internal_model.work.Work
import weco.pipeline_storage.typesafe.ElasticIndexerBuilder

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val identifiedIndex =
      Index(config.requireString("es.merged-works.index"))

    val esClient = ElasticBuilder.buildElasticClient(config)

    val workIndexer = ElasticIndexerBuilder[Work[Denormalised]](
      config,
      esClient,
      namespace = "denormalised-works",
      indexConfig = WorksIndexConfig.denormalised
    )

    new RelationEmbedderWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      msgSender = SNSBuilder
        .buildSNSMessageSender(
          config,
          subject = "Sent from the relation_embedder"),
      workIndexer = workIndexer,
      relationsService = new PathQueryRelationsService(
        esClient,
        identifiedIndex,
        completeTreeScroll = config.requireInt("es.works.scroll.complete_tree"),
        affectedWorksScroll =
          config.requireInt("es.works.scroll.affected_works")
      ),
      indexBatchSize = config.requireInt("es.works.batch_size"),
      indexFlushInterval =
        config.requireInt("es.works.flush_interval_seconds").seconds,
    )
  }
}
