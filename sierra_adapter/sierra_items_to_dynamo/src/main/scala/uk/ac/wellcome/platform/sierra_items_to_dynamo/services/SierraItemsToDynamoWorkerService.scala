package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import akka.Done
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.sierra_adapter.model.SierraItemRecord
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class SierraItemsToDynamoWorkerService[Destination](
                                        sqsStream: SQSStream[NotificationMessage],
                                        dynamoInserter: DynamoInserter,
                                        messageSender: BigMessageSender[Destination, SierraItemRecord]
)
    extends Runnable {

  private def process(message: NotificationMessage) =
    Future.fromTry{
      for {
      itemRecord <- fromJson[SierraItemRecord](message.body)
      vhsIndexEntry <- dynamoInserter.insertIntoDynamo(itemRecord).toTry
      _ <- messageSender.sendT(vhsIndexEntry)
    } yield ()
    }

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, process)
}
