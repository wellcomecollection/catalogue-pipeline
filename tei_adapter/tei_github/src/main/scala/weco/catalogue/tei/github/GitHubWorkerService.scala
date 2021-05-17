package weco.catalogue.tei.github

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

class GitHubWorkerService[Destination](
  messageStream: SQSStream[NotificationMessage],
  gitHubRetriever: GitHubRetriever,
  messageSender: MessageSender[Destination],
  concurrentWindows: Int)(implicit val ec: ExecutionContext)
    extends Runnable
    with FlowOps {

  val className = this.getClass.getSimpleName

  override def run(): Future[Any] = {
    messageStream.runStream(
      className,
      source =>
        source
          .via(unwrapMessage)
          .via(processWindow)
          .map { case (Context(msg), _) => msg })
  }

  def processWindow =
    Flow[(Context, Window)]
      .mapAsync(concurrentWindows) {
        case (ctx, window) => Future((ctx, Right(())))

      }
      .via(catchErrors)

  def unwrapMessage =
    Flow[(SQSMessage, NotificationMessage)]
      .map {
        case (msg, NotificationMessage(body)) =>
          (Context(msg), fromJson[Window](body).toEither)
      }
      .via(catchErrors)

  /** Encapsulates context to pass along each akka-stream stage. Newer versions
    * of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
    * this purpose, which we can migrate to if the library is updated.
    */
  case class Context(msg: SQSMessage)
}

case class Window(since: ZonedDateTime, until: ZonedDateTime)
