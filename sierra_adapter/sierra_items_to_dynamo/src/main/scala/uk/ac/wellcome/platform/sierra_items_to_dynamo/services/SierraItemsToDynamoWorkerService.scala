package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.SNSWriter
import uk.ac.wellcome.messaging.sqs.NotificationStream
import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class SierraItemsToDynamoWorkerService(
  notificationStream: NotificationStream[SierraItemRecord],
  dynamoInserter: DynamoInserter,
  snsWriter: SNSWriter
)(implicit ec: ExecutionContext)
    extends Runnable {

  def processMessage(sierraItemRecord: SierraItemRecord): Future[Unit] =
    for {
      vhsIndexEntry <- dynamoInserter.insertIntoDynamo(sierraItemRecord)
      _ <- snsWriter.writeMessage(
        message = vhsIndexEntry.hybridRecord,
        subject = s"Sent from ${this.getClass.getSimpleName}"
      )
    } yield ()

  def run(): Future[Done] =
    notificationStream.run(processMessage)
}
