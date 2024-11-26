package weco.pipeline.relation_embedder

import org.apache.pekko.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.messaging.sns.NotificationMessage
import weco.messaging.typesafe.SQSBuilder
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.EnrichConfig._
import weco.catalogue.internal_model.work.Work
import weco.pipeline_storage.elastic.ElasticIndexer

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
object Main extends WellcomeTypesafeApp {
  runWithConfig {
    config: Config =>
      implicit val actorSystem: ActorSystem =
        ActorSystem("main-actor-system")
      implicit val ec: ExecutionContext =
        actorSystem.dispatcher

      val identifiedIndex =
        Index(config.requireString("es.merged-works.index"))

      val esClient = ElasticBuilder.buildElasticClient(config)

      val workIndexer =
        new ElasticIndexer[Work[Denormalised]](
          client = esClient,
          index = Index(config.requireString(s"es.denormalised-works.index"))
        )

      val batchWriter = new BatchIndexWriter(
        workIndexer = workIndexer,
        maxBatchWeight = config.requireInt("es.works.batch_size"),
        maxBatchWait =
          config.requireInt("es.works.flush_interval_seconds").seconds
      )

      new RelationEmbedderWorkerService(
        sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
        downstream = Downstream(Some(config)),
        relationsService = new PathQueryRelationsService(
          esClient,
          identifiedIndex,
          completeTreeScroll =
            config.requireInt("es.works.scroll.complete_tree"),
          affectedWorksScroll =
            config.requireInt("es.works.scroll.affected_works")
        ),
        batchWriter = batchWriter
      )
  }
}
