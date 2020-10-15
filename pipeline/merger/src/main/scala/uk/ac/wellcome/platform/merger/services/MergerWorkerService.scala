package uk.ac.wellcome.platform.merger.services

import java.time.Instant

import akka.Done
import io.circe.Encoder

import scala.concurrent.{ExecutionContext, Future}
import uk.ac.wellcome.models.matcher.MatcherResult
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.{Work, WorkState}

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
      workSets <- Future.sequence {
        matcherResult.works.map { matchedIdentifiers =>
          playbackService.fetchAllWorks(matchedIdentifiers.identifiers.toList)
        }
      }
      lastUpdated = workSets.flatMap(_.flatten.map(_.state.modifiedTime)).max
      _ <- Future.sequence {
        workSets.map(applyMerge(_, lastUpdated))
      }
    } yield ()

  private def applyMerge(maybeWorks: Seq[Option[Work[WorkState.Source]]],
                         lastUpdated: Instant): Future[Unit] = {
    val merged = mergerManager
      .applyMerge(maybeWorks = maybeWorks)
      .lastUpdated(lastUpdated)
    val worksFuture = sendMessages(workSender, merged.works)
    val imagesFuture = sendMessages(imageSender, merged.images)
    for {
      _ <- worksFuture
      _ <- imagesFuture
    } yield ()
  }

  private def sendMessages[Destination, T](
    sender: MessageSender[Destination],
    items: Seq[T])(implicit encoder: Encoder[T]): Future[Seq[Unit]] =
    Future.sequence(
      items.map { item =>
        Future.fromTry(sender.sendT(item))
      }
    )
}
