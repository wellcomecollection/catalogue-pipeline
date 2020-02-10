package uk.ac.wellcome.calm_adapter

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import akka.Done
import akka.stream.scaladsl._
import grizzled.slf4j.Logging
import com.amazonaws.services.sqs.model.{Message => SQSMessage}

import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSMessageSender}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.bigmessaging.FlowOps

case class CalmWindow(date: LocalDate)

/** Processes SQS messages consisting of a daily window, and publishes any CALM
  *  records to the transformer that have been modified within this window.
  *
  *  Consists of the following stages:
  *  - Retrieve all CALM records which have modified field the same date as the
  *    window
  *  - Store these records in VHS, filtering out ones older than what is
  *    currently in  the store
  *  - Publish the VHS key to SNS
  */
class CalmAdapterWorkerService(
  msgStream: SQSStream[NotificationMessage],
  msgSender: SNSMessageSender,
  calmRetriever: CalmRetriever,
  calmStore: CalmStore,
  concurrentHttpConnections: Int = 3)(implicit val ec: ExecutionContext)
    extends Runnable
    with FlowOps
    with Logging {

  /** Encapsulates context to pass along each akka-stream stage. Newer versions
    *  of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
    *  this purpose, which we can migrate to if the library is updated.
    */
  case class Context(msg: SQSMessage)

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.runStream(
      className,
      source => {
        source
          .via(unwrapMessage)
          .via(retrieveCalmRecords)
          .via(storeCalmRecord)
          .via(publishKey)
          .map { case (Context(msg), _) => msg }
      }
    )

  def unwrapMessage =
    Flow[(SQSMessage, NotificationMessage)]
      .map {
        case (msg, NotificationMessage(body)) =>
          (msg, fromJson[CalmWindow](body).toEither)
      }
      .via(catchErrors)
      .map { case (msg, window) => (Context(msg), window) }

  def retrieveCalmRecords =
    Flow[(Context, CalmWindow)]
      .mapAsync(concurrentHttpConnections) {
        case (ctx, CalmWindow(date)) =>
          calmRetriever(CalmQuery.ModifiedDate(date))
            .transform(result => Success(result.toEither))
            .map(records => (ctx, records))
      }
      .via(catchErrors)
      .mapConcat {
        case (ctx, records) => records.map(record => (ctx, record))
      }

  def storeCalmRecord =
    Flow[(Context, CalmRecord)]
      .map { case (ctx, record) => (ctx, calmStore.putRecord(record)) }
      .via(catchErrors)

  def publishKey =
    Flow[(Context, Option[Version[String, Int]])]
      .mapWithContext {
        case (ctx, key) => msgSender.sendT(key).toEither.right.map(_ => key)
      }
}
