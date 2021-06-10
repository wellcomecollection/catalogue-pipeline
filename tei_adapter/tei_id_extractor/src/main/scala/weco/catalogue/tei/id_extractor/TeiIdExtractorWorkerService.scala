package weco.catalogue.tei.id_extractor

import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.{Message => SQSMessage}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.tei.id_extractor.database.TableProvisioner
import weco.catalogue.tei.id_extractor.models._
import weco.flows.FlowOps

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


case class TeiIdExtractorConfig(concurrentFiles: Int,
                                bucket: String)
class TeiIdExtractorWorkerService[Dest](messageStream: SQSStream[NotificationMessage],
                                        gitHubBlobReader: GitHubBlobContentReader,
                                        tableProvisioner: TableProvisioner,
                                        pathIdManager: PathIdManager[Dest],
                                        config: TeiIdExtractorConfig,
                                       )(implicit val ec: ExecutionContext)
    extends Runnable with FlowOps{
  val className = this.getClass.getSimpleName

  override def run()= for{
    _ <- Future(tableProvisioner.provision)
    _ <- runStream()
  } yield ()

  private def runStream(): Future[Any] = {
    messageStream.runStream(className,source =>
      source.via(unwrapMessage)
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

  def filterNonTei = Flow[(Context, TeiPathMessage)].filter{
    case (_, teiPathMessage) => !isTeiFile(teiPathMessage.path)
  }

  def processMessage =
    Flow[(Context, TeiPathMessage)].filter{
      case (_, teiPathMessage) => isTeiFile(teiPathMessage.path)
    }.via(broadcastAndMerge(processDeleted, processChange))

  def processDeleted = Flow[(Context, TeiPathMessage)].collectType[(Context, TeiPathDeletedMessage)]
    .delay(500 milliseconds)
    .mapAsync(config.concurrentFiles) { case (ctx, message) =>
      for {
        _ <- Future.fromTry(pathIdManager.handlePathDeleted(message.path, message.timeDeleted))
     } yield (ctx, Right(()))
    }.via(catchErrors)

  def processChange = Flow[(Context, TeiPathMessage)].collectType[(Context,TeiPathChangedMessage)]
      .mapAsync(config.concurrentFiles) {
        case (ctx, message) => for{
          blobContent <- gitHubBlobReader.getBlob(message.uri)
          id <- Future.fromTry(IdExtractor.extractId(blobContent))
          _ <- Future.fromTry(pathIdManager.handlePathChanged(PathId(message.path,id, message.timeModified), blobContent))
        } yield(ctx, Right(()))
      }
      .via(catchErrors)

  // Files in https://github.com/wellcomecollection/wellcome-collection-tei in any
  // directory that isn't "docs" or "Templates" and ends with .xml is a TEI file
  private def isTeiFile(path: String) = {
    val noTeiDirectories = Seq("docs", "Templates")
    !noTeiDirectories.exists(dir => path.startsWith(dir)) && path.endsWith(
      ".xml") && path.contains("/")
  }

  /** Encapsulates context to pass along each akka-stream stage. Newer versions
    * of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
    * this purpose, which we can migrate to if the library is updated.
    */
  case class Context(msg: SQSMessage)
}
