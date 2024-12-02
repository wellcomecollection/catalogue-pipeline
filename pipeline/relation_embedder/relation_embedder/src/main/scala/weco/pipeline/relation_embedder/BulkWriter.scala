package weco.pipeline.relation_embedder

import org.apache.pekko.NotUsed
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.pipeline_storage.Indexable.workIndexable
import org.apache.pekko.stream.scaladsl.Flow
import weco.pipeline_storage.Indexer

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Trait to handle the bulk writing of Works to a target in appropriately sized
  * batches.
  */
trait BulkWriter {
  // Maximum size (number of average documents) of batch to write
  val maxBatchWeight: Int = 100
  // maximum time to wait for a batch to reach the maximum weight
  val maxBatchWait: FiniteDuration = 20.seconds
  def writeWorksFlow
    : Flow[Work[Denormalised], Seq[Work[Denormalised]], NotUsed] =
    Flow[Work[Denormalised]]
      .via(groupedFlow)
      .mapAsync(1) {
        works => writeWorks(works)
      }

  private def groupedFlow
    : Flow[Work[Denormalised], Seq[Work[Denormalised]], NotUsed] =
    Flow[Work[Denormalised]].groupedWeightedWithin(
      maxBatchWeight,
      maxBatchWait
    )(workIndexable.weight)

  protected def writeWorks(
    works: Seq[Work[Denormalised]]
  ): Future[Seq[Work[Denormalised]]]
}

/** Controls the indexing of Works by Elasticsearch Bulk dividing the incoming
  * works into appropriate batches.
  */

class BulkIndexWriter(
  workIndexer: Indexer[Work[Denormalised]],
  override val maxBatchWeight: Int,
  override val maxBatchWait: FiniteDuration
)(implicit ec: ExecutionContext)
    extends BulkWriter {

  // init checks whether we can connect to the index named in workIndexer
  // There really is no point in doing anything else if it can't.
  Await.result(workIndexer.init(), Duration.Inf)

  protected def writeWorks(
    works: Seq[Work[Denormalised]]
  ): Future[Seq[Work[Denormalised]]] =
    workIndexer(works).flatMap {
      case Left(failedWorks) =>
        Future.failed(
          new Exception(s"Failed indexing works: $failedWorks")
        )
      case Right(_) => Future.successful(works.toList)
    }
}

/** Controls the indexing of Works by Elasticsearch Bulk dividing the incoming
  * works into appropriate batches.
  */

class BulkSTDOutWriter(
  override val maxBatchWeight: Int,
  override val maxBatchWait: FiniteDuration
) extends BulkWriter {

  protected def writeWorks(
    works: Seq[Work[Denormalised]]
  ): Future[Seq[Work[Denormalised]]] = {
    println(works.map {
      work =>
        val weight = workIndexable.weight(work)
        println(s"${work.id}, ${weight}")
        weight
    }.sum)

    Future.successful(works)
  }
}
