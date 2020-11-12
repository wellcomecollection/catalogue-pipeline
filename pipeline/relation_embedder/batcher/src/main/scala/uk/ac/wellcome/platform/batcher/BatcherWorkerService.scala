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
  batchFlushInterval: FiniteDuration = 10 seconds
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
          .mapAsync(1) { case (msgs, paths) => processPaths(msgs, paths) }
          .flatMapConcat(identity)
    )

  private def processPaths(
    msgs: List[SQSMessage],
    paths: List[String]): Future[Source[SQSMessage, NotUsed]] =
    generateSelectors(paths)
      .groupedWithin(batchSize, batchFlushInterval)
      .mapAsyncUnordered(10) { selectorGroups =>
        val (selectors, msgIndices) = selectorGroups.toList.unzip
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

  private def generateSelectors(
    paths: List[String]): Source[(Selector, Long), NotUsed] = {
    val selectors = paths.zipWithIndex.flatMap {
      case (path, idx) =>
        Selector.forPath(path).map(selector => (selector, idx))
    }
    val selectorSet = selectors.map(_._1).toSet
    Source(selectors).collect {
      case (selector, idx) if !shouldSupressSelector(selector, selectorSet) =>
        (selector, idx)
    }
  }

  private def shouldSupressSelector(selector: Selector,
                                    selectorSet: Set[Selector]): Boolean =
    selector.superSelectors.exists(selectorSet.contains(_))
}
