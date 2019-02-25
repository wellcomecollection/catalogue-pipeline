package uk.ac.wellcome.platform.sierra_bib_merger.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.SNSWriter
import uk.ac.wellcome.messaging.sqs.NotificationStream
import uk.ac.wellcome.models.transformable.sierra.SierraBibRecord
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class SierraBibMergerWorkerService(
  notificationStream: NotificationStream[SierraBibRecord],
  snsWriter: SNSWriter,
  sierraBibMergerUpdaterService: SierraBibMergerUpdaterService
)(implicit ec: ExecutionContext)
    extends Runnable {

  def run(): Future[Done] =
    notificationStream.run(processMessage)

  def processMessage(bibRecord: SierraBibRecord): Future[Unit] =
    for {
      vhsIndexEntry <- sierraBibMergerUpdaterService.update(bibRecord)
      _ <- snsWriter.writeMessage(
        vhsIndexEntry.hybridRecord,
        s"Sent from ${this.getClass.getSimpleName}")
    } yield ()
}
