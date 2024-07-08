package weco.pipeline.sierra_linker.services

import org.apache.pekko.Done
import io.circe.{Decoder, Encoder}
import weco.json.JsonUtil.fromJson
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.typesafe.Runnable
import weco.catalogue.source_model.sierra.AbstractSierraRecord
import weco.sierra.models.identifiers.TypedSierraRecordNumber

import scala.concurrent.Future
import scala.util.Success

class SierraLinkerWorker[
  Id <: TypedSierraRecordNumber,
  Record <: AbstractSierraRecord[Id],
  Destination
](
  sqsStream: SQSStream[NotificationMessage],
  linkStore: LinkStore[Id, Record],
  messageSender: MessageSender[Destination]
)(implicit decoder: Decoder[Record], encoder: Encoder[Record])
    extends Runnable {

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
