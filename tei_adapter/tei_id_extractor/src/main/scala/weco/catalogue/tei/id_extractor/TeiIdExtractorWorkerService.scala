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
import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}

case class TeiFileChangedMessage(path: String, uri: URI, timeModified: ZonedDateTime)
case class TeiIdChangeMessage(id: String, s3Location: S3ObjectLocation, timeModified: ZonedDateTime)

class TeiIdExtractorWorkerService[Dest](messageStream: SQSStream[NotificationMessage],
                                        gitHubBlobReader: GitHubBlobReader,
                                        idExtractor: IdExtractor,
                                        store: Writable[S3ObjectLocation, String],
                                        messageSender: MessageSender[Dest], concurrentFiles: Int, bucket: String)(implicit val ec: ExecutionContext)
    extends Runnable with FlowOps{
  val className = this.getClass.getSimpleName

  override def run(): Future[Any] = {
    messageStream.runStream(className,source =>
      source.via(unwrapMessage)
        .via(processMessage)
        .map { case (Context(msg), _) => msg })
  }

  def processMessage =
    Flow[(Context, TeiFileChangedMessage)]
      .mapAsync(concurrentFiles) {
        case (ctx, message) => for{
          blobContent <- gitHubBlobReader.getBlob(message.uri)
          id <- Future.fromTry(idExtractor.extractId(blobContent))
          stored <- Future.fromTry(store.put(S3ObjectLocation(bucket, s"tei_files/$id/${message.timeModified.toEpochSecond}.xml"))(blobContent).left.map(error => error.e).toTry)
          _ <- Future.fromTry(messageSender.sendT(TeiIdChangeMessage(id, stored.id, message.timeModified)))
        } yield(ctx, Right(()))
      }
      .via(catchErrors)

  def unwrapMessage =
    Flow[(SQSMessage, NotificationMessage)]
      .map {
        case (msg, NotificationMessage(body)) =>
          (Context(msg), fromJson[TeiFileChangedMessage](body).toEither)
      }
      .via(catchErrors)

  /** Encapsulates context to pass along each akka-stream stage. Newer versions
    * of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
    * this purpose, which we can migrate to if the library is updated.
    */
  case class Context(msg: SQSMessage)
}
