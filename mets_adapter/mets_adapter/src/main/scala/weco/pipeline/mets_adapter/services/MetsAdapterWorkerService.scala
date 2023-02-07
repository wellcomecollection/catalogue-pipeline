package weco.pipeline.mets_adapter.services

import akka.Done
import akka.stream.scaladsl._
import grizzled.slf4j.Logging
import software.amazon.awssdk.services.sqs.model.{Message => SQSMessage}
import weco.pipeline.mets_adapter.models._
import weco.catalogue.source_model.MetsSourcePayload
import weco.catalogue.source_model.mets.MetsSourceData
import weco.catalogue.source_model.Implicits._
import weco.flows.FlowOps
import weco.json.JsonUtil._
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.storage.store.VersionedStore
import weco.storage.{Identified, Version}
import weco.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** Processes SQS messages representing bag ingests on storage-service, and
  * publishes METS data for use in the pipeline.
  *
  * Consists of the following stages:
  *   - Retrieve the bag from storarge-service with the given ID
  *   - Parse METS data (which contains paths to the XML files) from the bag
  *   - Store the METS data in the VHS
  *   - Publish the VHS key to SNS
  */
class MetsAdapterWorkerService[Destination](
  msgStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[Destination],
  bagRetriever: BagRetriever,
  metsStore: VersionedStore[String, Int, MetsSourceData],
  concurrentHttpConnections: Int = 6,
  concurrentDynamoConnections: Int = 4
)(implicit val ec: ExecutionContext)
    extends Runnable
    with FlowOps
    with Logging {

  /** Encapsulates context to pass along each akka-stream stage. Newer versions
    * of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
    * this purpose, which we can migrate to if the library is updated.
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
          .via(parseMetsSourceData)
          .via(storeMetsSourceData)
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
          info(
            s"Skipping notification $notification because it is not in the digitised space"
          )
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

  def parseMetsSourceData =
    Flow[(Context, Option[Bag])]
      .mapWithContext { case (_, bag) => bag.metsSourceData }

  def storeMetsSourceData =
    Flow[(Context, Option[MetsSourceData])]
      .mapWithContextAsync(concurrentDynamoConnections) {
        case (ctx, sourceData: MetsSourceData) =>
          val id = Version(ctx.bagId, sourceData.version)

          Future {
            metsStore
              .put(id)(sourceData)
              .left
              .map(_.e)
          }
      }

  def publishKey =
    Flow[(Context, Option[Identified[Version[String, Int], MetsSourceData]])]
      .mapWithContext {
        case (_, Identified(Version(id, version), sourceData)) =>
          val sourcePayload = MetsSourcePayload(
            id = id,
            version = version,
            sourceData = sourceData
          )

          msgSender.sendT(sourcePayload) match {
            case Success(())  => Right(Version(id, version))
            case Failure(err) => Left(err)
          }
      }
}
