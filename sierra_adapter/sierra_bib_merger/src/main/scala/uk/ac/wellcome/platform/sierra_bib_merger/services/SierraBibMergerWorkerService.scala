package uk.ac.wellcome.platform.sierra_bib_merger.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.sierra_adapter.model.SierraBibRecord
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class SierraBibMergerWorkerService[Destination](
                                    sqsStream: SQSStream[NotificationMessage],
                                    messageSender: MessageSender[Destination],
                                    sierraBibMergerUpdaterService: SierraBibMergerUpdaterService
)
    extends Runnable {
  private def process(message: NotificationMessage): Future[Unit] =
    Future.fromTry(for {
      bibRecord <- fromJson[SierraBibRecord](message.body)
      vhsIndexEntry <- sierraBibMergerUpdaterService.update(bibRecord).toTry
      _ <- messageSender.sendT(vhsIndexEntry)
    } yield ())

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, process)
}
