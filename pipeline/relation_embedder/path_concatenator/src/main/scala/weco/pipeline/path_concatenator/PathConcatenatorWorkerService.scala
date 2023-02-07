package weco.pipeline.path_concatenator

import akka.Done
import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.Work
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.pipeline_storage.Indexer
import weco.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** Worker service that responds to SQS messages and updates Works with the
  * relevant paths.
  *
  * The service:
  *
  *   - takes messages from sqsStream
  *     - The messages are expected to contain path strings corresponding to
  *       collectionPath.path values.
  *   - uses pathsModifier to retrieve and modify relevant Works
  *   - saves the modified Works using workIndexer
  *   - notifies the downstream service using msgSender
  *     - The messages contain path strings corresponding to collectionPath.path
  *       values.
  *     - There will be a message for the input path retrieved from sqsStream
  *     - There will be a message containing any new paths created/changed by
  *       this service
  */
class PathConcatenatorWorkerService[MsgDestination](
  sqsStream: SQSStream[NotificationMessage],
  pathsModifier: PathsModifier,
  workIndexer: Indexer[Work[Merged]],
  msgSender: MessageSender[MsgDestination]
)(implicit ec: ExecutionContext)
    extends Runnable
    with Logging {

  def run(): Future[Done] = {
    workIndexer.init()
    sqsStream.foreach(this.getClass.getSimpleName, processMessage)
  }

  private def processMessage(message: NotificationMessage): Future[Unit] =
    processPath(message.body).map(_ => ())

  private def processPath(
    path: String
  ): Future[Seq[Unit]] = {
    val changedWorks = pathsModifier.modifyPaths(path)
    val futurePaths: Future[Seq[String]] = changedWorks transformWith {
      // workIndexer doesn't like empty lists
      case Success(Nil) => Future(Seq(path))
      case Success(works: Seq[Work[Merged]]) =>
        workIndexer(works)
          .map {
            case Right(works) => pathsToNotify(path, works)
            case Left(_)      => Seq(path)
          }
      case Failure(exception) => {
        // Even if a data error has prevented this stage working,
        // the originally requested path should be forwarded downstream.
        // This will allow the Work in question to progress through the pipeline
        // and eventually be indexed for presenting to the API,
        // even if its position in a path hierarchy is not correctly resolved.
        error(
          msg = s"Unable to update collectionPaths relating to $path",
          exception
        )
        Future(Seq(path))
      }
    }
    futurePaths flatMap {
      paths: Seq[String] =>
        notifyPaths(paths)
    }
  }

  // always send the original path from the incoming message.
  // batcher/relation embedder don't mind if paths don't resolve
  // and it's simpler than checking here whether the current path has changed
  private def pathsToNotify(
    path: String,
    works: Seq[Work[Merged]]
  ): Seq[String] =
    path +: works.map(work => work.data.collectionPath.get.path)

  private def notifyPaths(paths: Seq[String]): Future[Seq[Unit]] =
    Future.sequence(paths map {
      path =>
        Future(msgSender.send(path)).flatMap {
          case Success(_)   => Future.successful(())
          case Failure(err) => Future.failed(err)
        }
    })
}
