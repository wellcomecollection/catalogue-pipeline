package weco.pipeline.batcher

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try
import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.stream.Materializer
import software.amazon.awssdk.services.sqs.model.{Message => SQSMessage}
import grizzled.slf4j.Logging
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.typesafe.Runnable

case class Batch(rootPath: String, selectors: List[Selector])

class BatcherWorkerService[MsgDestination](
  msgStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[MsgDestination],
  pathsProcessor: PathsProcessor,
  flushInterval: FiniteDuration,
  maxProcessedPaths: Int
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Runnable
    with Logging {

  def run(): Future[Done] =
    msgStream.runStream(
      this.getClass.getSimpleName,
      (source: Source[(SQSMessage, NotificationMessage), NotUsed]) => {
        source
          .map {
            case (msg: SQSMessage, notificationMessage: NotificationMessage) =>
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
      }
    )

  /** Process a list of input paths by generating appropriate batches to send to
    * the relation embedder, deleting input paths from the SQS queue when the
    * corresponding batches have been succesfully sent.
    */
  private def processPaths(
    msgs: List[SQSMessage],
    paths: List[String]
  ): Future[Source[SQSMessage, NotUsed]] =
    pathsProcessor(paths)
      .map {
        failedIndices =>
          val failedIdxSet = failedIndices.toSet
          Source(msgs).zipWithIndex
            .collect {
              case (msg, idx) if !failedIdxSet.contains(idx) => msg
            }
      }
}
