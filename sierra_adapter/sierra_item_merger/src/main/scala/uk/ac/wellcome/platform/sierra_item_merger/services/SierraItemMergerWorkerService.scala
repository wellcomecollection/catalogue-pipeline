package uk.ac.wellcome.platform.sierra_item_merger.services

import akka.Done
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.platform.sierra_item_merger.store.{
  ItemStore,
  VersionExpectedConflictException
}
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.source_model.SierraSourcePayload

import scala.concurrent.{ExecutionContext, Future}

class SierraItemMergerWorkerService[Destination](
  sqsStream: SQSStream[NotificationMessage],
  sierraItemMergerUpdaterService: SierraItemMergerUpdaterService,
  itemRecordStore: ItemStore,
  messageSender: MessageSender[Destination]
)(implicit ec: ExecutionContext)
    extends Runnable
    with Logging {

  private def process(message: NotificationMessage): Future[Unit] = {
    val f = for {
      key <- fromJson[Version[String, Int]](message.body).toEither
      itemRecord <- itemRecordStore.get(key)
      updatedKeys <- sierraItemMergerUpdaterService
        .update(itemRecord)
        .left
        .map(id => id.e)
    } yield (updatedKeys)

    f match {
      case Right(updates)                           => sendUpdates(updates)
      case Left(VersionExpectedConflictException()) => Future.successful(())
      case Left(e)                                  => Future.failed(e)
    }
  }

  private def sendUpdates(
    updatedKeys: Seq[Identified[Version[String, Int], S3ObjectLocation]]) = {
    Future
      .sequence {
        updatedKeys
          .map {
            case Identified(Version(id, version), location) =>
              Future.fromTry {
                val payload = SierraSourcePayload(
                  id = id,
                  location = location,
                  version = version
                )

                messageSender.sendT(payload)
              }
          }
      }
      .map(_ => ())
  }

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, process)
}
