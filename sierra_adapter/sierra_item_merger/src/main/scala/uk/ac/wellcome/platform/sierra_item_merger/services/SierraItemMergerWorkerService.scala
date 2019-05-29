package uk.ac.wellcome.platform.sierra_item_merger.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, Entry}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class SierraItemMergerWorkerService[Destination](
                                                  sqsStream: SQSStream[NotificationMessage],
                                                  sierraItemMergerUpdaterService: SierraItemMergerUpdaterService,
                                                  itemStore: ObjectStore[SierraItemRecord],
                                                  messageSender: MessageSender[Destination]
)(implicit ec: ExecutionContext)
    extends Runnable {

  private def process(message: NotificationMessage): Future[Unit] =
    for {
      entry <- Future.fromTry(fromJson[Entry[String, EmptyMetadata]](message.body))

      // TODO: A bit of a hack to get the Either-based storage libraries working
      itemRecord <- itemStore.get(entry.location) match {
        case Right(value) => Future.successful(value)
        case Left(storageError) => Future.failed(storageError.e)
      }

      newEntries <- sierraItemMergerUpdaterService.update(itemRecord)
      _ <- Future.sequence(
        newEntries.map { entry =>
          Future.fromTry { messageSender.sendT(entry) }
        }
      )
    } yield ()

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, process)
}
