package weco.pipeline.batcher
import grizzled.slf4j.Logging
import weco.pipeline.batcher.models.{Batch, Path, PathFromString, Selector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class BatcherResponse(successes: Seq[Batch] = Seq.empty, failures: Seq[String] = Seq.empty)

/** Processes a list of paths by bundling them into Batches
 * @param maxBatchSize
 *   The maximum number of selectors to include in a single Batch
 */
class PathsProcessor(maxBatchSize: Int)(
  implicit ec: ExecutionContext
) extends Logging {

  /** Takes a list of strings, each representing a path to be processed by
   * _downstream_
   *
   * This processor bundles the input paths together into Batches suitable for
   * processing together
   *
   * @return
   *   A sequence of BatcherResponse with successes and/or failures
   *   The caller publishes the successes downstream
   *   and sends the failures back to be retried or to the DLQ
   */
  def process(paths: Seq[String]): Future[BatcherResponse] = {
    info(s"Processing ${paths.size} paths with max batch size $maxBatchSize")

    val result = generateBatches(maxBatchSize, paths.map(PathFromString))
    result match {
      case Success(batches) => Future(BatcherResponse(successes = batches))
      case Failure(ex) => Future(BatcherResponse(failures = paths))
    }
  }



  /** Given a list of input paths, generate the minimal set of selectors
   * matching works needing to be denormalised, and group these according to
   * tree and within a maximum `batchSize`.
   */
  def generateBatches(
    maxBatchSize: Int,
    paths: Seq[Path]
  ): Try[Seq[Batch]] = {
    Try {
      val selectors = Selector.forPaths(paths)
      val groupedSelectors = selectors.groupBy(_._1.rootPath)

      logSelectors(paths, selectors, groupedSelectors)

      groupedSelectors.map {
        case (rootPath, selectorsAndPaths) =>
          // For batches consisting of a really large number of selectors, we
          // should just send the whole tree: this avoids really long queries
          // in the relation embedder, or duplicate work of creating the archives
          // cache multiple times, and it is likely pretty much all the nodes will
          // be denormalised anyway.
          val (selectors, _) = selectorsAndPaths.unzip(identity)
          if (selectors.size > maxBatchSize)
            Batch(rootPath, List(Selector.Tree(rootPath)))
          else
            Batch(rootPath, selectors)
      }.toSeq
    }
  }

  private def logSelectors(
    paths: Seq[Path],
    selectors: List[(Selector, Path)],
    groupedSelectors: Map[String, List[(Selector, Path)]]
  ): Unit = {
    info(
      s"Generated ${selectors.size} selectors spanning ${groupedSelectors.size} trees from ${paths.size} paths."
    )
    paths.sorted.grouped(1000).toList.zipWithIndex.foreach {
      case (paths, idx) =>
        val startIdx = idx * 1000 + 1
        info(
          s"Input paths ($startIdx-${startIdx + paths.length - 1}): ${paths.mkString(", ")}"
        )
    }
    groupedSelectors.foreach {
      case (rootPath, selectors) =>
        info(
          s"Selectors for root path $rootPath: ${selectors.map(_._1).mkString(", ")}"
        )
    }
  }
}

object PathsProcessor {
  def apply(
    maxBatchSize: Int
  )(implicit ec: ExecutionContext): PathsProcessor =
    new PathsProcessor(maxBatchSize)
}
