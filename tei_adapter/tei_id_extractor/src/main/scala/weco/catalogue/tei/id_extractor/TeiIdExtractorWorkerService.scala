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
import weco.flows.FlowOps

import java.net.URI
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

// Represents a path change coming from the tei_updater lambda
case class TeiFileChangedMessage(path: String, uri: URI, timeModified: Instant)

// Represents a message for the tei_adapter with changes to id instead of file path
case class TeiIdChangeMessage(id: String,
                              s3Location: S3ObjectLocation,
                              timeModified: Instant)
case class TeiIdExtractorConfig(concurrentFiles: Int, bucket: String)

class TeiIdExtractorWorkerService[Dest](
  messageStream: SQSStream[NotificationMessage],
  gitHubBlobReader: GitHubBlobContentReader,
  store: Writable[S3ObjectLocation, String],
  messageSender: MessageSender[Dest],
  config: TeiIdExtractorConfig)(implicit val ec: ExecutionContext)
    extends Runnable
    with FlowOps {
  val className = this.getClass.getSimpleName

  override def run(): Future[Any] = {
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
          (Context(msg), fromJson[TeiFileChangedMessage](body).toEither)
      }
      .via(catchErrors)

  def filterNonTei = Flow[(Context, TeiFileChangedMessage)].filter {
    case (_, TeiFileChangedMessage(path, _, _)) => !isTeiFile(path)
  }

  def processMessage =
    Flow[(Context, TeiFileChangedMessage)]
      .filter {
        case (_, TeiFileChangedMessage(path, _, _)) => isTeiFile(path)
      }
      .mapAsync(config.concurrentFiles) {
        case (ctx, message) =>
          for {
            blobContent <- gitHubBlobReader.getBlob(message.uri)
            id <- Future.fromTry(
              IdExtractor.extractId(blobContent, message.uri))
            stored <- Future.fromTry(
              store
                .put(S3ObjectLocation(
                  config.bucket,
                  s"tei_files/$id/${message.timeModified.getEpochSecond}.xml"))(
                  blobContent)
                .left
                .map(error => error.e)
                .toTry)
            _ <- Future.fromTry(
              messageSender.sendT(
                TeiIdChangeMessage(id, stored.id, message.timeModified)))
          } yield (ctx, Right(()))
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
