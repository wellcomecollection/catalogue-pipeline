package uk.ac.wellcome.platform.merger.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.BigMessageSender
import uk.ac.wellcome.messaging.message.MessageNotification
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.matcher.{MatchedIdentifiers, MatcherResult}
import uk.ac.wellcome.models.work.internal.BaseWork
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class MergerWorkerService[Destination](
  sqsStream: SQSStream[NotificationMessage],
  playbackService: RecorderPlaybackService,
  mergerManager: MergerManager,
  messageSender: BigMessageSender[Destination, BaseWork]
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
      works: Seq[BaseWork] = mergerManager.applyMerge(maybeWorks = maybeWorks)
      _ <- sendWorks(works)
    } yield ()

  private def sendWorks(
    mergedWorks: Seq[BaseWork]): Future[Seq[MessageNotification]] =
    Future
      .sequence(
        mergedWorks.map(work => Future.fromTry { messageSender.sendT(work) }))
}
