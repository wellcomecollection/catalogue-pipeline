package weco.catalogue.sierra_merger.services

import akka.Done
import grizzled.slf4j.Logging
import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.sierra_adapter.model.AbstractSierraRecord
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.source_model.SierraSourcePayload

import scala.concurrent.{ExecutionContext, Future}

class Worker[Record <: AbstractSierraRecord[_], Destination](
  sqsStream: SQSStream[NotificationMessage],
  updater: Updater[Record],
  messageSender: MessageSender[Destination]
)(implicit
  ec: ExecutionContext,
  decoder: Decoder[Record])
    extends Runnable
    with Logging {

  private def process(message: NotificationMessage): Future[Unit] = {
    val f = for {
      record <- fromJson[Record](message.body).toEither
      updatedKeys <- updater
        .update(record)
        .left
        .map(id => id.e)
    } yield updatedKeys

    f match {
      case Right(updates) => sendUpdates(updates)
      case Left(e)        => Future.failed(e)
    }
  }

  private def sendUpdates(
    updatedKeys: Seq[Identified[Version[String, Int], S3ObjectLocation]])
    : Future[Unit] =
    Future
      .sequence {
        updatedKeys
          .map {
            case Identified(Version(id, version), location) =>
              Future.fromTry {
                val payload = SierraSourcePayload(
                  id = id,
                  location = location,
                  version = version
                )

                messageSender.sendT(payload)
              }
          }
      }
      .map(_ => ())

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, process)
}
