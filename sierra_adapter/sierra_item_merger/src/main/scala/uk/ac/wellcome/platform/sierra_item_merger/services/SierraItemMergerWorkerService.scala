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
import cats.implicits._

import scala.concurrent.Future

class SierraItemMergerWorkerService[Destination](
  sqsStream: SQSStream[NotificationMessage],
  sierraItemMergerUpdaterService: SierraItemMergerUpdaterService,
  itemRecordStore: VersionedStore[String, Int, SierraItemRecord],
  messageSender: MessageSender[Destination]
) extends Runnable {

  private def process(message: NotificationMessage): Future[Unit] =
    Future.fromTry {
      val f: Either[Throwable, Unit] = for {
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
        _ <- sendKeys(updatedKeys)
      } yield ()
      f.toTry
    }

  private def sendKeys(
    updatedKeys: List[Version[String, Int]]): Either[Throwable, List[Unit]] = {
    val tries: List[Either[Throwable, Unit]] = updatedKeys.par.map { key =>
      messageSender.sendT(key).toEither
    }.toList
    tries.sequence
  }

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, process)
}
