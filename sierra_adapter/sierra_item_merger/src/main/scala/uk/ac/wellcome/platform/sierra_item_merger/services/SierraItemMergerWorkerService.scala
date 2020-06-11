package uk.ac.wellcome.platform.sierra_item_merger.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.sierra_adapter.model.SierraItemRecord
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class SierraItemMergerWorkerService[Destination](
  sqsStream: SQSStream[NotificationMessage],
  sierraItemMergerUpdaterService: SierraItemMergerUpdaterService,
  itemRecordStore: VersionedStore[String, Int, SierraItemRecord],
  messageSender: MessageSender[Destination]
) (implicit ec: ExecutionContext)extends Runnable {

  private def process(message: NotificationMessage): Future[Unit] = {

    val f= for {
      key <- fromJson[Version[String, Int]](message.body).toEither
      itemRecord <- itemRecordStore
        .get(key)
        .map(id => id.identifiedT)
        .left
        .map(id => id.e)
      updatedKeys <- sierraItemMergerUpdaterService
        .update(itemRecord)
        .left
        .map(id => id.e)
    } yield (updatedKeys)
    Future.fromTry(f.toTry).flatMap{ updatedKeys =>
      sendKeys(updatedKeys)
    }
  }

  private def sendKeys(
    updatedKeys: Seq[Version[String, Int]]) = {
    Future.sequence {
      updatedKeys
        .map { key =>
          Future.fromTry {
            messageSender.sendT(key)
          }
        }
    }.map(_ =>())
  }

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, process)
}
