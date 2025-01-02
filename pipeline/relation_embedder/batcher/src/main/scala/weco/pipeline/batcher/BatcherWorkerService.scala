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
      (source: Source[(SQSMessage, NotificationMessage), NotUsed]) => {
        source
          .map {
            case (msg: SQSMessage, notificationMessage: NotificationMessage) =>
              PathFromSQS(notificationMessage.body, msg)
          }
          .groupedWithin(maxProcessedPaths, flushInterval)
          .mapAsync(1) {
            paths =>
              info(s"Processing ${paths.size} input paths")
              processPaths(paths)
          }
          .flatMapConcat(identity)
      }
    )

  /** Process a list of input paths by generating appropriate batches to send to
    * the relation embedder, deleting input paths from the SQS queue when the
    * corresponding batches have been succesfully sent.
    */
  private def processPaths(
    paths: Seq[PathFromSQS]
  ): Future[Source[SQSMessage, NotUsed]] =
    PathsProcessor(maxBatchSize, paths, SNSDownstream)
      .map {
        failedPaths =>
          val failedPathSet = failedPaths.toSet
          Source(paths.collect {
            case path if !failedPathSet.contains(path) => path.referent
          }.toList)
      }

  private object SNSDownstream extends Downstream {
    override def notify(batch: Batch): Try[Unit] = msgSender.sendT(batch)
  }
}
