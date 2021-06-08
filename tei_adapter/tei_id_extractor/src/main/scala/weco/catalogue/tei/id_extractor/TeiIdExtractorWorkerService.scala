package weco.catalogue.tei.id_extractor

import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.{Message => SQSMessage}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Writable
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.tei.id_extractor.database.TableProvisioner
import weco.catalogue.tei.id_extractor.models._
import weco.flows.FlowOps

import scala.concurrent.{ExecutionContext, Future}


case class TeiIdExtractorConfig(concurrentFiles: Int,
                                bucket: String,
                                teiDirectories: Set[String] = Set("Arabic", "Batak", "Egyptian", "Greek", "Hebrew", "Indic", "Javanese", "Malay"))

class TeiIdExtractorWorkerService[Dest](messageStream: SQSStream[NotificationMessage],
                                        gitHubBlobReader: GitHubBlobReader,
                                        tableProvisioner: TableProvisioner,
                                        idExtractor: IdExtractor,
                                        store: Writable[S3ObjectLocation, String],
                                        messageSender: MessageSender[Dest],
                                        pathIdManager: PathIdManager,
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

  def processDeleted = Flow[(Context, TeiPathMessage)].collectType[(Context,TeiPathDeletedMessage)]
    .mapAsync(config.concurrentFiles) { case (ctx, message) =>
for{
  maybeId <- pathIdManager.handlePathDeleted(message.path, message.timeDeleted)
  _ <- Future.fromTry(messageSender.sendT[TeiIdMessage](TeiIdDeletedMessage(maybeId.get.id, message.timeDeleted)))
} yield ((ctx, Right(())))
    }.via(catchErrors)

  def processChange = Flow[(Context, TeiPathMessage)].collectType[(Context,TeiPathChangedMessage)]
      .mapAsync(config.concurrentFiles) {
        case (ctx, message) => for{
          blobContent <- gitHubBlobReader.getBlob(message.uri)
          id <- Future.fromTry(idExtractor.extractId(blobContent))
          _ <- pathIdManager.handlePathChanged(message.path,id, message.timeModified)
          stored <- Future.fromTry(store.put(S3ObjectLocation(config.bucket, s"tei_files/$id/${message.timeModified.toEpochSecond}.xml"))(blobContent).left.map(error => error.e).toTry)
          _ <- Future.fromTry(messageSender.sendT[TeiIdMessage](TeiIdChangeMessage(id, stored.id, message.timeModified)))
        } yield(ctx, Right(()))
      }
      .via(catchErrors)


  private def isTeiFile(path: String) = {
    config.teiDirectories.exists(dir => path.startsWith(dir)) && path.endsWith(".xml")
  }

  /** Encapsulates context to pass along each akka-stream stage. Newer versions
    * of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
    * this purpose, which we can migrate to if the library is updated.
    */
  case class Context(msg: SQSMessage)
}
