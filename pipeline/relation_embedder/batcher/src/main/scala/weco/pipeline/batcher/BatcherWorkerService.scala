package weco.pipeline.batcher

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.{Done, NotUsed}
import akka.stream.scaladsl._
import akka.stream.Materializer
import software.amazon.awssdk.services.sqs.model.{Message => SQSMessage}
import grizzled.slf4j.Logging

import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.typesafe.Runnable
import weco.json.JsonUtil._

case class Batch(rootPath: String, selectors: List[Selector])

class BatcherWorkerService[MsgDestination](
  msgStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[MsgDestination],
  flushInterval: FiniteDuration,
  maxProcessedPaths: Int,
  maxBatchSize: Int
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Runnable
    with Logging {

  def run(): Future[Done] =
    msgStream.runStream(
      this.getClass.getSimpleName,
      source =>
        source
          .map {
            case (msg, notificationMessage) =>
              (msg, notificationMessage.body)
          }
          .groupedWithin(maxProcessedPaths, flushInterval)
          .map(_.toList.unzip)
          .mapAsync(1) {
            case (msgs, paths) =>
              info(s"Processing ${paths.size} input paths")
              processPaths(msgs, paths)
          }
          .flatMapConcat(identity)
    )

  /** Process a list of input paths by generating appropriate batches to send to
    * the relation embedder, deleting input paths from the SQS queue when the
    * corresponding batches have been succesfully sent.
    */
  private def processPaths(
    msgs: List[SQSMessage],
    paths: List[String]
  ): Future[Source[SQSMessage, NotUsed]] =
    generateBatches(paths)
      .mapAsyncUnordered(10) {
        case (batch, msgIndices) =>
          Future {
            msgSender.sendT(batch) match {
              case Success(_) => None
              case Failure(err) =>
                error(s"Failed processing batch $batch with error: $err")
                Some(msgIndices)
            }
          }
      }
      .collect { case Some(failedIndices) => failedIndices }
      .mapConcat(identity)
      .runWith(Sink.seq)
      .map {
        failedIndices =>
          val failedIdxSet = failedIndices.toSet
          Source(msgs).zipWithIndex
            .collect {
              case (msg, idx) if !failedIdxSet.contains(idx) => msg
            }
      }

  /** Given a list of input paths, generate the minimal set of selectors
    * matching works needing to be denormalised, and group these according to
    * tree and within a maximum `batchSize`.
    */
  private def generateBatches(
    paths: List[String]
  ): Source[(Batch, List[Long]), NotUsed] = {
    val selectors = Selector.forPaths(paths)
    val groupedSelectors = selectors.groupBy(_._1.rootPath)
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
    Source(groupedSelectors.toList).map {
      case (rootPath, selectorsAndIndices) =>
        // For batches consisting of a really large number of selectors, we
        // should just send the whole tree: this avoids really long queries
        // in the relation embedder, or duplicate work of creating the archives
        // cache multiple times, and it is likely pretty much all the nodes will
        // be denormalised anyway.
        val (selectors, msgIndices) = selectorsAndIndices.unzip(identity)
        val batch =
          if (selectors.size > maxBatchSize)
            Batch(rootPath, List(Selector.Tree(rootPath)))
          else
            Batch(rootPath, selectors)
        batch -> msgIndices
    }
  }
}
