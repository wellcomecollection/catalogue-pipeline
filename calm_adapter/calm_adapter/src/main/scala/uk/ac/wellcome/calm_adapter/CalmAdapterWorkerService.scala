package uk.ac.wellcome.calm_adapter

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import akka.Done
import akka.stream.scaladsl._
import akka.stream.ActorMaterializer
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
  * records to the transformer that have been modified within this window.
  *
  * Consists of the following stages:
  * - Retrieve all CALM records which have modified field the same date as the
  *   window
  * - Store these records in VHS, filtering out ones older than what is
  *   currently in  the store
  * - Publish the VHS key to SNS
  */
class CalmAdapterWorkerService(msgStream: SQSStream[NotificationMessage],
                               msgSender: SNSMessageSender,
                               calmRetriever: CalmRetriever,
                               calmStore: CalmStore,
                               concurrentWindows: Int = 2)(
  implicit
  val ec: ExecutionContext,
  materializer: ActorMaterializer)
    extends Runnable
    with FlowOps
    with Logging {

  type Key = Version[String, Int]

  /** Encapsulates context to pass along each akka-stream stage. Newer versions
    * of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
    * this purpose, which we can migrate to if the library is updated.
    */
  case class Context(msg: SQSMessage)

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.runStream(
      className,
      source =>
        source
          .via(unwrapMessage)
          .via(processWindow)
          .map { case (Context(msg), _) => msg }
    )

  def unwrapMessage =
    Flow[(SQSMessage, NotificationMessage)]
      .map {
        case (msg, NotificationMessage(body)) =>
          (Context(msg), fromJson[CalmWindow](body).toEither)
      }
      .via(catchErrors)

  /** We process the whole window as a single batch, rather than demultiplexing
    * into stream of individual records. This is because we need to emit exactly
    * one delete action per message received.
    */
  def processWindow =
    Flow[(Context, CalmWindow)]
      .mapAsync(concurrentWindows) {
        case (ctx, CalmWindow(date)) =>
          calmRetriever(CalmQuery.ModifiedDate(date))
            .map(calmStore.putRecord)
            .via(publishKey)
            .via(updatePublished)
            .runWith(Sink.seq)
            .map(checkResultsForErrors(_, date))
            .map((ctx, _))
      }
      .via(catchErrors)

  def publishKey =
    Flow[Result[Option[(Key, CalmRecord)]]]
      .map {
        case Right(Some((key, record))) =>
          msgSender.sendT(key).toEither.right.map(_ => Some(key -> record))
        case Right(None) => Right(None)
        case err         => err
      }

  def updatePublished =
    Flow[Result[Option[(Key, CalmRecord)]]]
      .map {
        case Right(Some((key, record))) =>
          calmStore.setRecordPublished(key, record)
        case Right(None) => Right(None)
        case err         => err
      }

  def checkResultsForErrors(results: Seq[Result[_]],
                            date: LocalDate): Result[Unit] = {
    val errs = results.collect { case Left(err) => err }.toList
    if (errs.nonEmpty)
      Left(new Exception(s"Errors processing window $date: $errs"))
    else
      Right(())
  }
}
