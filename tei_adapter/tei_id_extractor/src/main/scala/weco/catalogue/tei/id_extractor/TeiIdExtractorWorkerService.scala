package weco.catalogue.tei.id_extractor

import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.{Message => SQSMessage}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.typesafe.Runnable
import weco.flows.FlowOps

import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}

case class TeiFileChangedMessage(path: String, uri: Uri, timeModified: ZonedDateTime)

class TeiIdExtractorWorkerService[Dest](messageStream: SQSStream[NotificationMessage],
                                        gitHubBlobReader: GitHubBlobReader,
                                             messageSender: MessageSender[Dest], concurrentFiles: Int)(implicit val ec: ExecutionContext)
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
          result <- gitHubBlobReader.getBlob(message.uri)
        } yield(ctx, result)
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
