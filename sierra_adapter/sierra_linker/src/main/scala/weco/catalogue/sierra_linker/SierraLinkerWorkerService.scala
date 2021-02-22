package weco.catalogue.sierra_linker

import akka.Done
import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.sierra_adapter.model.{
  AbstractSierraRecord,
  SierraTypedRecordNumber
}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future
import scala.util.Success

class SierraLinkerWorkerService[Id <: SierraTypedRecordNumber,
                                Record <: AbstractSierraRecord[Id],
                                Destination](
  sqsStream: SQSStream[NotificationMessage],
  linkStore: LinkingRecordStore[Id, Record],
  messageSender: MessageSender[Destination]
)(
  implicit decoder: Decoder[Record],
  encoder: Encoder[Record]
) extends Runnable {

  private def process(message: NotificationMessage): Future[Unit] =
    Future.fromTry {
      for {
        record <- fromJson[Record](message.body)
        record <- linkStore.update(record).toTry
        _ <- record match {
          case Some(k) => messageSender.sendT(k)
          case None    => Success(())
        }
      } yield ()
    }

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, process)
}
