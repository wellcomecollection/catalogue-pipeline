package weco.pipeline.relation_embedder

import org.apache.pekko.NotUsed
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.pipeline_storage.Indexable.workIndexable
import org.apache.pekko.stream.scaladsl.Source
import weco.pipeline_storage.Indexer

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

trait BatchWriter {
  // Maximum size (number of average documents) of batch to write
  val maxBatchWeight: Int = 100
  // maximum time to wait for a batch to reach the maximum weight
  val maxBatchWait: FiniteDuration = 20.seconds

  def writeBatch(
    works: Source[Work[Denormalised], NotUsed]
  ): Source[Seq[Work[Denormalised]], NotUsed] =
    grouped(works)
      .mapAsync(1) {
        works => writeWorks(works)
      }

  private def grouped(denormalisedWorks: Source[Work[Denormalised], NotUsed]) =
    denormalisedWorks
      .groupedWeightedWithin(
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

class BatchIndexWriter(
  workIndexer: Indexer[Work[Denormalised]],
  override val maxBatchWeight: Int,
  override val maxBatchWait: FiniteDuration
)(implicit ec: ExecutionContext)
    extends BatchWriter {

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

class BatchSTDOutWriter(
  override val maxBatchWeight: Int,
  override val maxBatchWait: FiniteDuration
) extends BatchWriter {

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
