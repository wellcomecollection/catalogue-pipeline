package uk.ac.wellcome.platform.merger.services

import akka.Done
import io.circe.Encoder

import scala.concurrent.{ExecutionContext, Future}
import uk.ac.wellcome.models.matcher.{MatchedIdentifiers, MatcherResult}
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream

class MergerWorkerService[WorkDestination, ImageDestination](
  sqsStream: SQSStream[NotificationMessage],
  playbackService: RecorderPlaybackService,
  mergerManager: MergerManager,
  workSender: MessageSender[WorkDestination],
  imageSender: MessageSender[ImageDestination]
)(implicit ec: ExecutionContext)
    extends Runnable {

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, processMessage)

  private def processMessage(message: NotificationMessage): Future[Unit] =
    for {
      matcherResult <- Future.fromTry(fromJson[MatcherResult](message.body))
      _ <- Future.sequence(matcherResult.works.map { applyMerge })
    } yield ()

  private def applyMerge(matchedIdentifiers: MatchedIdentifiers): Future[Unit] =
    for {
      maybeWorks <- playbackService.fetchAllWorks(
        matchedIdentifiers.identifiers.toList)
      merged = mergerManager.applyMerge(maybeWorks = maybeWorks)
      (worksFuture, imagesFuture) = (
        sendMessages(workSender, merged.works),
        sendMessages(imageSender, merged.images)
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
