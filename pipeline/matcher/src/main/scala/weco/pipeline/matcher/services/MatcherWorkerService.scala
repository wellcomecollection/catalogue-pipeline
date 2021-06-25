package weco.pipeline.matcher.services

import akka.Done
import grizzled.slf4j.Logging
import weco.json.JsonUtil._
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.catalogue.internal_model.Implicits._
import weco.pipeline_storage.PipelineStorageStream._
import weco.pipeline_storage.PipelineStorageConfig
import weco.pipeline.matcher.exceptions.MatcherException
import weco.pipeline.matcher.matcher.WorkMatcher
import weco.pipeline.matcher.models.{VersionExpectedConflictException, WorkLinks}
import weco.typesafe.Runnable
import weco.pipeline_storage.{PipelineStorageConfig, Retriever}

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
    msgStream.runStream(
      this.getClass.getSimpleName,
      source =>
        source
          .via(batchRetrieveFlow(config, workLinksRetriever))
          .mapAsync(config.parallelism) {
            case (message, item) =>
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
