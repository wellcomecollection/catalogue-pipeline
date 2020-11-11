package uk.ac.wellcome.platform.router

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.{Done, NotUsed}
import akka.stream.scaladsl._
import akka.stream.Materializer

import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._

class BatcherWorkerService[MsgDestination](
  msgStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[MsgDestination],
  batchSize: Int = 100000,
  flushInterval: FiniteDuration = 30 minutes
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Runnable {

  def run(): Future[Done] =
    msgStream.runStream(
      this.getClass.getSimpleName,
      source =>
        source
          .map { case (msg, notificationMessage) =>
            (msg, notificationMessage.body)
          }
          .groupedWithin(batchSize, flushInterval)
          .mapAsync(1) { msgsAndPaths =>
            val (msgs, paths) = msgsAndPaths.unzip
            batchPaths(paths)
              .mapAsync(10) { path =>
                Future {
                  msgSender.send(path).toOption match {
                    case Some(_) => None
                    case None    => Some(path)
                  }
                }
              }
              .collect { case Some(failedPath) => failedPath }
              .runWith(Sink.seq)
              .map { failedPaths =>
              }
          }
          .mapConcat(identity)
    )

  def batchPaths(paths: Seq[String]): Source[String, NotUsed] =
    ???
}
