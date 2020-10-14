package uk.ac.wellcome.platform.merger.services

import scala.concurrent.{ExecutionContext, Future}
import akka.Done
import io.circe.Encoder

import uk.ac.wellcome.models.matcher.{MatchedIdentifiers, MatcherResult}
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.pipeline_storage.Indexer
import WorkState.Merged

class MergerWorkerService[WorkDestination, ImageDestination](
  sqsStream: SQSStream[NotificationMessage],
  playbackService: RecorderPlaybackService,
  mergerManager: MergerManager,
  workIndexer: Indexer[Work[Merged]],
  workSender: MessageSender[WorkDestination],
  imageSender: MessageSender[ImageDestination]
)(implicit ec: ExecutionContext)
    extends Runnable {

  def run(): Future[Done] =
    workIndexer.init().flatMap { _ =>
      sqsStream.foreach(this.getClass.getSimpleName, processMessage)
    }

  private def processMessage(message: NotificationMessage): Future[Unit] =
    for {
      matcherResult <- Future.fromTry(fromJson[MatcherResult](message.body))
      _ <- Future.sequence(matcherResult.works.map { applyMerge })
    } yield ()

  private def applyMerge(matchedIdentifiers: MatchedIdentifiers): Future[Unit] =
    for {
      maybeWorks <- playbackService.fetchAllWorks(
        matchedIdentifiers.identifiers.toList)
      mergerOutcome = mergerManager.applyMerge(maybeWorks = maybeWorks)
      indexResult <- workIndexer.index(mergerOutcome.works)
      works <- indexResult match {
        case Left(failedWorks) =>
          Future.failed(new Exception(s"Failed indexing works: $failedWorks"))
        case Right(works) => Future.successful(works)
      }
      (worksFuture, imagesFuture) = (
        sendMessages(workSender, works.map(_.id)),
        sendMessages(imageSender, mergerOutcome.images)
      )
      _ <- worksFuture
      _ <- imagesFuture
    } yield ()

  private def sendMessages[Destination, T](
    sender: MessageSender[Destination],
    items: Seq[T])(implicit encoder: Encoder[T]): Future[Seq[Unit]] =
    Future.sequence(
      items.map { item =>
        Future.fromTry(sender.sendT(item))
      }
    )
}
