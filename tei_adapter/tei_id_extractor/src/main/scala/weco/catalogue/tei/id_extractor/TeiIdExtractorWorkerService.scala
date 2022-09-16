package weco.catalogue.tei.id_extractor

import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.{Message => SQSMessage}
import weco.json.JsonUtil.fromJson
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.typesafe.Runnable
import weco.catalogue.tei.id_extractor.database.TableProvisioner
import weco.catalogue.source_model.tei.{
  TeiPathChangedMessage,
  TeiPathDeletedMessage,
  TeiPathMessage
}
import weco.catalogue.tei.id_extractor.models.PathId
import weco.flows.FlowOps

import scala.concurrent.{ExecutionContext, Future}

class TeiIdExtractorWorkerService[Dest](
  messageStream: SQSStream[NotificationMessage],
  gitHubBlobReader: GitHubBlobContentReader,
  tableProvisioner: TableProvisioner,
  pathIdManager: PathIdManager[Dest],
  config: TeiIdExtractorConfig,
)(implicit val ec: ExecutionContext)
    extends Runnable
    with FlowOps {
  val className = this.getClass.getSimpleName

  override def run() =
    for {
      _ <- Future(tableProvisioner.provision())
      _ <- runStream()
    } yield ()

  private def runStream(): Future[Any] = {
    messageStream.runStream(
      className,
      source =>
        source
          .via(unwrapMessage)
          .via(broadcastAndMerge(filterNonTei, processMessage))
          .map { case (Context(msg), _) => msg })
  }

  def unwrapMessage =
    Flow[(SQSMessage, NotificationMessage)]
      .map {
        case (msg, NotificationMessage(body)) =>
          (Context(msg), fromJson[TeiPathMessage](body).toEither)
      }
      .via(catchErrors)

  def filterNonTei = Flow[(Context, TeiPathMessage)].filter {
    case (_, teiPathMessage) => !isTeiFile(teiPathMessage.path)
  }

  def processMessage =
    Flow[(Context, TeiPathMessage)]
      .filter {
        case (_, teiPathMessage) => isTeiFile(teiPathMessage.path)
      }
      .via(broadcastAndMerge(processDeleted, processChange))

  def processDeleted =
    Flow[(Context, TeiPathMessage)]
      .collect {
        case (ctx, msg) if msg.isInstanceOf[TeiPathDeletedMessage] =>
          (ctx, msg.asInstanceOf[TeiPathDeletedMessage])
      }
      // When something is moved in the repo the updater lambda will send a changed message
      // for the new path and a delete message for the old path. We don't want the delete message
      // to be processed first because it could make thing disappear temporarily from the api
      // (the change message will override the deleted message changes eventually). So we're introducing
      // a delay for deleted messages so that changed messages are processed first
      .delay(config.deleteMessageDelay)
      .mapAsync(config.parallelism) {
        case (ctx, message) =>
          for {
            _ <- Future.fromTry(
              pathIdManager
                .handlePathDeleted(message.path, message.timeDeleted))
          } yield (ctx, Right(()))
      }
      .via(catchErrors)

  def processChange =
    Flow[(Context, TeiPathMessage)]
      .collect {
        case (ctx, msg) if msg.isInstanceOf[TeiPathChangedMessage] =>
          (ctx, msg.asInstanceOf[TeiPathChangedMessage])
      }
      .mapAsync(config.parallelism) {
        case (ctx, message) =>
          for {
            blobContent <- gitHubBlobReader.getBlob(message.uri)
            id <- Future.fromTry(
              IdExtractor.extractId(blobContent, message.path))
            _ <- Future.fromTry(
              pathIdManager.handlePathChanged(
                PathId(message.path, id, message.timeModified),
                blobContent))
          } yield (ctx, Right(()))
      }
      .via(catchErrors)

  /** Is this a TEI file we want to process as part of the pipeline? */
  private def isTeiFile(path: String): Boolean = {
    val isXmlFile = path.endsWith(".xml")

    val isInRootOfRepo = path.contains("/")

    val excludedDirs = Seq(
      // These are directories used for TEI management, not actual TEI files.
      "docs",
      "Templates",
      // These are files we don't want to ingest directly into the platform;
      // e.g. stuff that's been written for collaboration on external projects.
      //
      // See https://github.com/wellcomecollection/catalogue-pipeline/issues/2197#issuecomment-1249665377
      "Arabic/Fihrist"
    )
    val isInExcludedDir = excludedDirs.exists(dir => path.startsWith(dir))

    isXmlFile && !isInRootOfRepo && !isInExcludedDir
  }

  /** Encapsulates context to pass along each akka-stream stage. Newer versions
    * of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
    * this purpose, which we can migrate to if the library is updated.
    */
  case class Context(msg: SQSMessage)
}
