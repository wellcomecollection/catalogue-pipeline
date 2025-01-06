package weco.pipeline.batcher
import grizzled.slf4j.Logging
import org.apache.pekko.stream.Materializer
import weco.lambda.Downstream
import weco.json.JsonUtil._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** Processes a list of paths by bundling them into Batches and sending them to
  * a downstream service for processing.
  *
  * @param downstream
  *   The downstream target to send the Batches to
  * @param maxBatchSize
  *   The maximum number of selectors to include in a single Batch
  */
class PathsProcessor(downstream: Downstream, maxBatchSize: Int)(
  implicit ec: ExecutionContext
) extends Logging {

  /** Takes a list of strings, each representing a path to be processed by
    * _downstream_
    *
    * This processor bundles the input paths together into Batches suitable for
    * processing together, then passes those Batches on to _downstream_ for
    * actual processing.
    *
    * @return
    *   A sequence representing the positions within the input list of any paths
    *   that were not successfully processed (That still seems a bit
    *   SQS/SNS-driven. Should just be the actual failed paths, and the caller
    *   should build a map to work it out if it wants to)
    */
  def apply[T <: Path](paths: Seq[T]): Future[Seq[Path]] = {
    info(s"Processing ${paths.size} paths with max batch size $maxBatchSize")

    Future
      .sequence {
        generateBatches(maxBatchSize, paths).map {
          case (batch, msgPaths) =>
            notifyDownstream(downstream, batch, msgPaths)
        }
      }
      .flatMap {
        results =>
          Future {
            results.collect {
              case Some(failedPaths) => failedPaths
            }.flatten
          }
      }
  }

  private def notifyDownstream(
    downstream: Downstream,
    batch: Batch,
    msgPaths: List[Path]
  )(
    implicit ec: ExecutionContext
  ): Future[Option[List[Path]]] = {
    Future {
      downstream.notify(batch) match {
        case Success(_) => None
        case Failure(err) =>
          error(s"Failed processing batch $batch with error: $err")
          Some(msgPaths)
      }
    }
  }

  /** Given a list of input paths, generate the minimal set of selectors
    * matching works needing to be denormalised, and group these according to
    * tree and within a maximum `batchSize`.
    */
  private def generateBatches(
    maxBatchSize: Int,
    paths: Seq[Path]
  ): Seq[(Batch, List[Path])] = {
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
        val (selectors, inputPaths) = selectorsAndPaths.unzip(identity)
        val batch =
          if (selectors.size > maxBatchSize)
            Batch(rootPath, List(Selector.Tree(rootPath)))
          else
            Batch(rootPath, selectors)
        batch -> inputPaths
    }.toSeq
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
    downstream: Downstream,
    maxBatchSize: Int
  )(implicit ec: ExecutionContext, mat: Materializer): PathsProcessor =
    new PathsProcessor(downstream, maxBatchSize)
}
