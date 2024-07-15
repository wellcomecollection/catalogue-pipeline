package weco.pipeline.sierra_merger.services

import org.apache.pekko.Done
import grizzled.slf4j.Logging
import io.circe.Decoder
import weco.json.JsonUtil.fromJson
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.{Identified, Version}
import weco.typesafe.Runnable
import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.source_model.sierra.AbstractSierraRecord
import weco.catalogue.source_model.Implicits._

import scala.concurrent.{ExecutionContext, Future}

class Worker[SierraRecord <: AbstractSierraRecord[_], Destination](
  sqsStream: SQSStream[NotificationMessage],
  updater: Updater[SierraRecord],
  messageSender: MessageSender[Destination]
)(implicit ec: ExecutionContext, decoder: Decoder[SierraRecord])
    extends Runnable
    with Logging {

  private def process(message: NotificationMessage): Future[Unit] = {
    val f = for {
      record <- fromJson[SierraRecord](message.body).toEither
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
    updatedKeys: Seq[Identified[Version[String, Int], S3ObjectLocation]]
  ): Future[Unit] =
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
