package uk.ac.wellcome.platform.matcher.services

import akka.Done
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream._
import uk.ac.wellcome.pipeline_storage.{PipelineStorageConfig, Retriever}
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.models.{VersionExpectedConflictException, WorkLinks}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class MatcherWorkerService[MsgDestination](
                                            config: PipelineStorageConfig,
  workLinksRetriever: Retriever[WorkLinks],
  msgStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[MsgDestination],
  workMatcher: WorkMatcher)(implicit ec: ExecutionContext)
    extends Logging
    with Runnable {

  def run(): Future[Done] =
    msgStream.runStream(this.getClass.getSimpleName, source =>
      source
        .via(batchRetrieveFlow(config, workLinksRetriever))
        .mapAsync(config.parallelism){ case (message, item) =>
      processMessage(item).map(_ => message)
        }
    )

  def processMessage(workLinks: WorkLinks): Future[Unit] = {
    (for {
      identifiersList <- workMatcher.matchWork(workLinks)
      _ <- Future.fromTry(msgSender.sendT(identifiersList))
    } yield ()).recover {
      case MatcherException(e: VersionExpectedConflictException) =>
        debug(
          s"Not matching work due to version conflict exception: ${e.getMessage}")
    }
  }
}
