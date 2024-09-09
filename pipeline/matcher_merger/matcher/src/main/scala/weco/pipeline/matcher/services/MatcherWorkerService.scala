package weco.pipeline.matcher.services

import org.apache.pekko.Done
import grizzled.slf4j.Logging
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.pipeline_storage.PipelineStorageStream._
import weco.pipeline.matcher.matcher.WorkMatcher
import weco.pipeline.matcher.models.MatcherResult._
import weco.pipeline.matcher.models.{
  MatcherResult,
  VersionExpectedConflictException,
  WorkStub
}
import weco.typesafe.Runnable
import weco.pipeline_storage.{PipelineStorageConfig, Retriever}

import scala.concurrent.{ExecutionContext, Future}

trait Worker[T, Output] {
  def doWork(t: T): Output
}

trait MatcherWorker
    extends Worker[WorkStub, Future[Option[MatcherResult]]]
    with Logging {
  implicit val ec: ExecutionContext
  val workMatcher: WorkMatcher

  def doWork(workStub: WorkStub): Future[Option[MatcherResult]] = {
    workMatcher
      .matchWork(workStub)
      .map(matcherResult => {
        info(s"Matching works: $matcherResult; for work: ${workStub.id}")
        Some(matcherResult)
      })
      .recover {
        case e: VersionExpectedConflictException =>
          debug(
            s"Not matching work due to version conflict exception: ${e.getMessage}"
          )
          None
      }
  }
}

class CommandLineMatcherWorkerService(
  retriever: Retriever[WorkStub],
  val workMatcher: WorkMatcher
)(val workId: Option[String])(implicit val ec: ExecutionContext)
    extends MatcherWorker
    with Runnable {

  def run(): Future[Unit] = workId match {
    case Some(workId) => runWithId(workId)
    case None => Future.failed(new RuntimeException("No work ID provided"))
  }

  private def runWithId(workId: String): Future[Unit] =
    retriever
      .apply(workId)
      .flatMap(doWork)
      .map { _.foreach(printResults) }

  private def printResults(matcherResult: MatcherResult): Unit = {
    info(s"Matcher result (${matcherResult.works.flatMap(_.identifiers).toList.length}): ${matcherResult.works.flatMap(_.identifiers.map(_.identifier)).toList.reverse.mkString(",")}")
  }
}

class MatcherWorkerService[MsgDestination](
  config: PipelineStorageConfig,
  retriever: Retriever[WorkStub],
  msgStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[MsgDestination],
  val workMatcher: WorkMatcher
)(implicit val ec: ExecutionContext)
    extends MatcherWorker
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

  def processMessage(workStub: WorkStub): Future[Unit] = {
    doWork(workStub).flatMap {
      case Some(matcherResult) =>
        Future.fromTry(msgSender.sendT(matcherResult))
      case None =>
        Future.successful(())
    }
  }
}
