package uk.ac.wellcome.platform.merger.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.message.MessageWriter
import uk.ac.wellcome.messaging.sns.PublishAttempt
import uk.ac.wellcome.messaging.sqs.NotificationStream
import uk.ac.wellcome.models.matcher.{MatchedIdentifiers, MatcherResult}
import uk.ac.wellcome.models.work.internal.BaseWork
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class MergerWorkerService(
  notificationStream: NotificationStream[MatcherResult],
  playbackService: RecorderPlaybackService,
  mergerManager: MergerManager,
  messageWriter: MessageWriter[BaseWork]
)(implicit ec: ExecutionContext)
    extends Runnable {

  def run(): Future[Done] =
    notificationStream.run {
      matcherResult: MatcherResult =>
        Future.sequence {
          matcherResult.works.map {
            applyMerge
          }
        }.map { _ => () }
    }

  private def applyMerge(matchedIdentifiers: MatchedIdentifiers): Future[Unit] =
    for {
      maybeWorks <- playbackService.fetchAllWorks(
        matchedIdentifiers.identifiers.toList)
      works: Seq[BaseWork] = mergerManager.applyMerge(maybeWorks = maybeWorks)
      _ <- sendWorks(works)
    } yield ()

  private def sendWorks(mergedWorks: Seq[BaseWork]): Future[Seq[PublishAttempt]] =
    Future
      .sequence(
        mergedWorks.map(
          messageWriter.write(_, "merged-work")
        ))
}
