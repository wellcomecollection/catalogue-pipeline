package uk.ac.wellcome.platform.batcher

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.{Done, NotUsed}
import akka.stream.scaladsl._
import akka.stream.Materializer
import software.amazon.awssdk.services.sqs.model.{Message => SQSMessage}
import grizzled.slf4j.Logging

import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._

class BatcherWorkerService[MsgDestination](
  msgStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[MsgDestination],
  maxGroupedMessages: Int = 100000,
  flushInterval: FiniteDuration = 30 minutes,
  batchSize: Int = 20,
  batchFlushInterval: FiniteDuration = 100 milliseconds
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Runnable
    with Logging {

  case class Batch(selectors: List[Selector])

  def run(): Future[Done] =
    msgStream.runStream(
      this.getClass.getSimpleName,
      source =>
        source
          .map {
            case (msg, notificationMessage) =>
              (msg, notificationMessage.body)
          }
          .groupedWithin(maxGroupedMessages, flushInterval)
          .map(_.toList.unzip)
          .mapAsync(1) { case (msgs, paths) => 
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
    paths: List[String]): Future[Source[SQSMessage, NotUsed]] =
    generateSelectorBatches(paths)
      .map(_.toList.unzip)
      .mapAsyncUnordered(10) { case (selectors, msgIndices) =>
        Future {
          msgSender.sendT(Batch(selectors)) match {
            case Success(_) => None
            case Failure(err) =>
              error(err)
              Some(msgIndices)
          }
        }
      }
      .collect { case Some(failedIndices) => failedIndices }
      .mapConcat(identity)
      .runWith(Sink.seq)
      .map { failedIndices =>
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
  private def generateSelectorBatches(
    paths: List[String]): Source[Seq[(Selector, Long)], NotUsed] = {
    val selectors = Selector.forPaths(paths)
    val groupedSelectors = selectors.groupBy(_._1.rootPath).values.toList
    info(
      s"Generated ${selectors.size} selectors spanning ${groupedSelectors.size} trees from ${paths.size} paths."
    )
    Source(groupedSelectors).flatMapConcat {
      selectors =>
        Source(selectors).groupedWithin(batchSize, batchFlushInterval)
    }
  }
}
