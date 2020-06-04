package uk.ac.wellcome.mets_adapter.services

import scala.util.Success
import scala.concurrent.ExecutionContext
import akka.Done
import akka.stream.scaladsl._
import software.amazon.awssdk.services.sqs.model.{Message => SQSMessage}
import grizzled.slf4j.Logging
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSMessageSender}
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.mets_adapter.models._
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.bigmessaging.FlowOps

import scala.concurrent.Future

/** Processes SQS messages representing bag ingests on storage-service, and
  *  publishes METS data for use in the pipeline.
  *
  *  Consists of the following stages:
  *  - Retrieve the bag from storarge-service with the given ID
  *  - Parse METS data (which contains paths to the XML files) from the bag
  *  - Store the METS data in the VHS
  *  - Publish the VHS key to SNS
  */
class MetsAdapterWorkerService(
  msgStream: SQSStream[NotificationMessage],
  msgSender: SNSMessageSender,
  bagRetriever: BagRetriever,
  metsStore: MetsStore,
  concurrentHttpConnections: Int = 6,
  concurrentDynamoConnections: Int = 4)(implicit val ec: ExecutionContext)
    extends Runnable
    with FlowOps
    with Logging {

  /** Encapsulates context to pass along each akka-stream stage. Newer versions
    *  of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
    *  this purpose, which we can migrate to if the library is updated.
    */
  case class Context(msg: SQSMessage, bagId: String)

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.runStream(
      className,
      source => {
        source
          .via(unwrapMessage)
          .via(filterDigitised)
          .via(retrieveBag)
          .via(parseMetsLocation)
          .via(storeMetsLocation)
          .via(publishKey)
          .map { case (Context(msg, _), _) => msg }
      }
    )

  def unwrapMessage =
    Flow[(SQSMessage, NotificationMessage)]
      .map {
        case (msg, NotificationMessage(body)) =>
          (msg, fromJson[BagRegistrationNotification](body).toEither)
      }
      .via(catchErrors)
      .map {
        case (msg, notification) =>
          info(s"Processing notification $notification")
          (Context(msg, notification.externalIdentifier), notification)
      }

  // Bags in the storage service are grouped by "space", e.g. "digitised" or
  // "born-digital".
  //
  // For the catalogue pipeline, we're only interested in the digitised content,
  // so we can discard everything else.
  def filterDigitised =
    Flow[(Context, BagRegistrationNotification)]
      .map {
        case (ctx, notification) if notification.space == "digitised" =>
          (ctx, Some(notification))
        case (ctx, notification) =>
          info(s"Skipping notification $notification because it is not in the digitised space")
          (ctx, None)
      }

  def retrieveBag =
    Flow[(Context, Option[BagRegistrationNotification])]
      .mapWithContextAsync(concurrentHttpConnections) {
        case (_, notification) =>
          bagRetriever
            .getBag(
              space = notification.space,
              externalIdentifier = notification.externalIdentifier
            )
            .transform(result => Success(result.toEither))
      }

  def parseMetsLocation =
    Flow[(Context, Option[Bag])]
      .mapWithContext { case (_, bag) => bag.metsLocation }

  def storeMetsLocation =
    Flow[(Context, Option[MetsLocation])]
      .mapWithContextAsync(concurrentDynamoConnections) {
        case (ctx, data) =>
          Future {
            metsStore.storeData(Version(ctx.bagId, data.version), data)
          }
      }

  def publishKey =
    Flow[(Context, Option[Version[String, Int]])]
      .mapWithContext {
        case (_, data) => msgSender.sendT(data).toEither.right.map(_ => data)
      }
}
