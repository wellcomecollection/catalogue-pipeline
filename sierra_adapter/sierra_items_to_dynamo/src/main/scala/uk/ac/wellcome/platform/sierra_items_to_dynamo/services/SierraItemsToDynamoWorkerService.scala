package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class SierraItemsToDynamoWorkerService[Destination](
  sqsStream: SQSStream[NotificationMessage],
  vhsInserter: VHSInserter,
  messageSender: MessageSender[Destination]
) extends Runnable {

  private def process(message: NotificationMessage): Try[Unit] =
    for {
      itemRecord <- fromJson[SierraItemRecord](message.body)

      entry <- vhsInserter.insertIntoVhs(itemRecord) match {
        case Right(value) => Success(value)
        case Left(storageError) => Failure(storageError.e)
      }

      _ <- messageSender.sendT(entry)
    } yield ()

  def run(): Future[Done] =
    sqsStream.foreach(
      this.getClass.getSimpleName,
      message => Future.fromTry { process(message) }
    )
}
