package uk.ac.wellcome.platform.sierra_bib_merger.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.transformable.sierra.SierraBibRecord
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class SierraBibMergerWorkerService[Destination](
  sqsStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Destination],
  sierraBibMergerUpdaterService: SierraBibMergerUpdaterService
) extends Runnable {

  private def process(message: NotificationMessage): Try[Unit] =
    for {
      bibRecord <- fromJson[SierraBibRecord](message.body)

      // TODO: This is a bit nasty, and was added as a way to get the newer
      // storage libraries working in the catalogue.  Revisit and improve!
      vhsEntry <- sierraBibMergerUpdaterService.update(bibRecord) match {
        case Right(value) => Success(value)
        case Left(storageError) => Failure(storageError.e)
      }

      _ <- messageSender.sendT(vhsEntry)
    } yield ()

  def run(): Future[Done] =
    sqsStream.foreach(
      this.getClass.getSimpleName,
      message => Future.fromTry { process(message) }
    )
}
