package weco.pipeline.relation_embedder

import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import grizzled.slf4j.Logging
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.pipeline.relation_embedder.models.{
  ArchiveRelationsCache,
  Batch,
  RelationWork
}
import weco.pipeline_storage.elastic.ElasticIndexer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import weco.catalogue.internal_model.Implicits._
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.duration._

class BatchProcessor(
  relationsService: RelationsService,
  batchWriter: BulkWriter,
  downstream: Downstream
)(
  implicit ec: ExecutionContext,
  materializer: Materializer
) extends Logging {
  def apply(
    batch: Batch
  ): Future[Unit] = {

    info(
      s"Received batch for tree ${batch.rootPath} containing ${batch.selectors.size} selectors: ${batch.selectors
          .mkString(", ")}"
    )
    fetchRelations(relationsService, batch)
      .flatMap {
        relationsCache =>
          info(
            s"Built cache for tree ${batch.rootPath}, containing ${relationsCache.size} relations (${relationsCache.numParents} works map to parent works)."
          )
          indexWorks(denormaliseAll(batch, relationsCache))

      }
  }

  private def fetchRelations(
    relationsService: RelationsService,
    batch: Batch
  ): Future[ArchiveRelationsCache] =
    relationsService
      .getRelationTree(batch)
      .runWith(Sink.seq)
      .map {
        relationWorks: Seq[RelationWork] =>
          info(
            s"Received ${relationWorks.size} relations for tree ${batch.rootPath}"
          )
          ArchiveRelationsCache(relationWorks)
      }

  private def denormaliseAll(
    batch: Batch,
    relationsCache: ArchiveRelationsCache
  ): Source[Work[Denormalised], NotUsed] =
    relationsService
      .getAffectedWorks(batch)
      .map {
        work =>
          val relations = relationsCache(work)
          work.transition[Denormalised](relations)
      }

  private def indexWorks(
    denormalisedWorks: Source[Work[Denormalised], NotUsed]
  ) =
    batchWriter
      .writeBatch(denormalisedWorks)
      .mapConcat(_.map(_.id))
      .mapAsync(3) {
        id =>
          Future(downstream.notify(id)).flatMap {
            case Success(_)   => Future.successful(())
            case Failure(err) => Future.failed(err)
          }
      }
      .runWith(Sink.ignore)
      .map(_ => ())
}

object BatchProcessor {

  def apply(
    config: Config
  )(
    implicit actorSystem: ActorSystem,
    ec: ExecutionContext,
    materializer: Materializer
  ): BatchProcessor = {

    val identifiedIndex =
      Index(config.requireString("es.merged-works.index"))

    val esClient = ElasticBuilder.buildElasticClient(config)

    val workIndexer =
      new ElasticIndexer[Work[Denormalised]](
        client = esClient,
        index = Index(config.requireString(s"es.denormalised-works.index"))
      )

    val batchWriter = new BulkIndexWriter(
      workIndexer = workIndexer,
      maxBatchWeight = config.requireInt("es.works.batch_size"),
      maxBatchWait =
        config.requireInt("es.works.flush_interval_seconds").seconds
    )

    new BatchProcessor(
      relationsService = new PathQueryRelationsService(
        esClient,
        identifiedIndex,
        completeTreeScroll = config.requireInt("es.works.scroll.complete_tree"),
        affectedWorksScroll =
          config.requireInt("es.works.scroll.affected_works")
      ),
      batchWriter = batchWriter,
      downstream = Downstream(Some(config))
    )

  }
}
