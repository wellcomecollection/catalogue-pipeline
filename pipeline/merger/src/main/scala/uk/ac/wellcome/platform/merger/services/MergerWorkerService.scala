package uk.ac.wellcome.platform.merger.services

import java.time.Instant

import akka.Done
import io.circe.Encoder

import scala.concurrent.{ExecutionContext, Future}
import uk.ac.wellcome.models.matcher.MatcherResult
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
    val outcome = mergerManager.applyMerge(maybeWorks = maybeWorks)
    for {
      indexResult <- workIndexer.index(outcome.mergedWorksWithTime(lastUpdated))
      works <- indexResult match {
        case Left(failedWorks) =>
          Future.failed(new Exception(s"Failed indexing works: $failedWorks"))
        case Right(works) => Future.successful(works)
      }
      (worksFuture, imagesFuture) = (
        sendWorks(works),
        sendMessages(imageSender, outcome.mergedImagesWithTime(lastUpdated))
      )
      _ <- worksFuture
      _ <- imagesFuture
    } yield ()
  }

  private def sendWorks(works: Seq[Work[Merged]]): Future[Seq[Unit]] =
    Future.sequence(
      works.map { w =>
        Future.fromTry(workSender.send(w.id))
      }
    )

  private def sendMessages[Destination, T](
    sender: MessageSender[Destination],
    items: Seq[T])(implicit encoder: Encoder[T]): Future[Seq[Unit]] =
    Future.sequence(
      items.map { item =>
        Future.fromTry(sender.sendT(item))
      }
    )
}
