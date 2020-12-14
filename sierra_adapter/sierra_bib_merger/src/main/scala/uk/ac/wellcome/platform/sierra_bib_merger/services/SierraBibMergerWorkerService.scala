package uk.ac.wellcome.platform.sierra_bib_merger.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.sierra_adapter.model.SierraBibRecord
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.source_model.SierraSourcePayload

import scala.concurrent.Future
import scala.util.Success

class SierraBibMergerWorkerService[Destination](
  sqsStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Destination],
  sierraBibMergerUpdaterService: SierraBibMergerUpdaterService
) extends Runnable {

  private def process(message: NotificationMessage): Future[Unit] =
    Future.fromTry(for {
      bibRecord <- fromJson[SierraBibRecord](message.body)
      key <- sierraBibMergerUpdaterService.update(bibRecord).toTry
      _ <- key match {
        case Some(Identified(Version(id, version), location)) =>
          val payload = SierraSourcePayload(
            id = id,
            location = location,
            version = version
          )

          messageSender.sendT(payload)

        case _       => Success(())
      }
    } yield ())

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, process)
}
