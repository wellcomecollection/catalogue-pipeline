package weco.pipeline.relation_embedder

import com.sksamuel.elastic4s.Index
import grizzled.slf4j.Logging
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.pipeline.relation_embedder.models.{
  ArchiveRelationsCache,
  Batch,
  RelationWork
}
import weco.pipeline_storage.elastic.ElasticIndexer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import weco.catalogue.internal_model.Implicits._
import weco.elasticsearch.typesafe.ElasticBuilder
import weco.lambda.Downstream
import weco.pipeline.relation_embedder.lib.RelationEmbedderConfig

class BatchProcessor(
  relationsService: RelationsService,
  bulkWriter: BulkWriter,
  downstream: Downstream
)(
  implicit ec: ExecutionContext,
  materializer: Materializer
) extends Logging {

  /** Process a single Batch as sent to the Relation Embedder This involves:
    *   - pull the relevant (sub) tree from the upstream index
    *   - for any works referenced by the selectors in the Batch, update their
    *     Relations
    *     - Write any affected works downstream
    */
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
          debug(s"transitioning ${work.id}")
          val relations = relationsCache(work)
          work.transition[Denormalised](relations)
      }

  private def indexWorks(
    denormalisedWorks: Source[Work[Denormalised], NotUsed]
  ) =
    denormalisedWorks
      .via(bulkWriter.writeWorksFlow)
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
    config: RelationEmbedderConfig
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): BatchProcessor = {

    val denormalisedIndex =
      Index(config.denormalisedWorkIndex)

    val esClient = ElasticBuilder.buildElasticClient(config.elasticConfig)

    val workIndexer =
      new ElasticIndexer[Work[Denormalised]](
        client = esClient,
        index = Index(config.denormalisedWorkIndex)
      )

    val batchWriter = new BulkIndexWriter(
      workIndexer = workIndexer,
      maxBatchWeight = config.maxBatchWeight
    )

    new BatchProcessor(
      relationsService = new PathQueryRelationsService(
        esClient,
        denormalisedIndex,
        completeTreeScroll = config.completeTreeScroll,
        affectedWorksScroll = config.affectedWorksScroll
      ),
      bulkWriter = batchWriter,
      downstream = Downstream(config.downstreamTarget)
    )
  }
}
