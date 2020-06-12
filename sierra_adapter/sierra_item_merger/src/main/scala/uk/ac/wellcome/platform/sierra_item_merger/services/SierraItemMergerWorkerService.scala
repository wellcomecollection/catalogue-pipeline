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
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.typesafe.Runnable

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
      case Right(updateKeys)                        => sendKeys(updateKeys)
      case Left(VersionExpectedConflictException()) => Future.successful(())
      case Left(e)                                  => Future.failed(e)
    }
  }

  private def sendKeys(updatedKeys: Seq[Version[String, Int]]) = {
    Future
      .sequence {
        updatedKeys
          .map { key =>
            Future.fromTry {
              messageSender.sendT(key)
            }
          }
      }
      .map(_ => ())
  }

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, process)
}
