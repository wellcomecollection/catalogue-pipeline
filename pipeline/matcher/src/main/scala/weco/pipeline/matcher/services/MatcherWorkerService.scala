package weco.pipeline.matcher.services

import akka.Done
import grizzled.slf4j.Logging
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.pipeline_storage.PipelineStorageStream._
import weco.pipeline.matcher.matcher.WorkMatcher
import weco.pipeline.matcher.models.MatcherResult._
import weco.pipeline.matcher.models.{VersionExpectedConflictException, WorkStub}
import weco.typesafe.Runnable
import weco.pipeline_storage.{PipelineStorageConfig, Retriever}

import scala.concurrent.{ExecutionContext, Future}

class MatcherWorkerService[MsgDestination](
  config: PipelineStorageConfig,
  retriever: Retriever[WorkStub],
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
          .via(batchRetrieveFlow(config, retriever))
          .mapAsync(config.parallelism) {
            case (message, workStub) =>
              processMessage(workStub).map(_ => message)
        }
    )

  def processMessage(workStub: WorkStub): Future[Unit] =
    workMatcher
      .matchWork(workStub)
      .flatMap { matcherResult =>
        Future.fromTry(msgSender.sendT(matcherResult))
      }
      .recover {
        case e: VersionExpectedConflictException =>
          debug(
            s"Not matching work due to version conflict exception: ${e.getMessage}")
      }
}
