package uk.ac.wellcome.platform.router

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Success, Failure}
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
  batchSize: Int = 100000,
  flushInterval: FiniteDuration = 30 minutes
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Runnable with Logging {

  type Idx = Long
  type Path = String

  def run(): Future[Done] =
    msgStream.runStream(
      this.getClass.getSimpleName,
      source =>
        source
          .map {
            case (msg, notificationMessage) =>
              (msg, notificationMessage.body)
          }
          .groupedWithin(batchSize, flushInterval)
          .map(_.toList.unzip)
          .mapAsync(1) { case (msgs, paths) => processBatch(msgs, paths) }
          .flatMapConcat(identity)
    )


  private def processBatch(msgs: List[SQSMessage], paths: List[Path]): Future[Source[SQSMessage, NotUsed]] =
    getOutputPaths(paths)
      .mapAsyncUnordered(10) { case (path, msgIdx) =>
        Future {
          msgSender.send(path) match {
            case Success(_)   => None
            case Failure(err) =>
              error(err)
              Some(msgIdx)
          }
        }
      }
      .collect { case Some(failedIdx) => failedIdx }
      .runWith(Sink.seq)
      .map { failedIdxs =>
        val failedIdxSet = failedIdxs.toSet
        Source(msgs)
          .zipWithIndex
          .collect {
            case (msg, idx) if !failedIdxSet.contains(idx) => msg
          }
      }

  private def getOutputPaths(paths: List[Path]): Source[(Path, Idx), NotUsed] = {
    val pathSet = paths.toSet
    Source(paths)
      .zipWithIndex
      .collect {
        case (path, idx) if shouldOutputPath(path, pathSet) => (path, idx)
      }
  }

  private def shouldOutputPath(path: Path, pathSet: Set[Path]): Boolean =
    !pathAncestors(path).exists(pathSet.contains(_))

  private def pathAncestors(path: Path): List[Path] =
    tokenize(path) match {
      case head :+ tail :+ _ =>
        val ancestor = join(head :+ tail)
        pathAncestors(ancestor) :+ ancestor
      case _ => Nil
    }

  private def tokenize(path: Path): List[String] =
    path.split("/").toList

  private def join(tokens: List[String]): Path =
    tokens.mkString("/")
}
