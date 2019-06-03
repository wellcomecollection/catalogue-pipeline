package uk.ac.wellcome.platform.goobi_reader.services

import java.io.InputStream

import akka.Done
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs._
import uk.ac.wellcome.platform.goobi_reader.models.{
  GoobiRecordMetadata,
  S3Event,
  S3Record
}
import uk.ac.wellcome.storage.vhs.VersionedHybridStore
import uk.ac.wellcome.storage.{ObjectStore, StorageError}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class GoobiReaderWorkerService(
  s3ObjectStore: ObjectStore[InputStream],
  sqsStream: SQSStream[NotificationMessage],
  vhs: VersionedHybridStore[String, InputStream, GoobiRecordMetadata]
)(implicit ec: ExecutionContext)
    extends Logging
    with Runnable {

  def run(): Future[Done] =
    sqsStream.foreach(
      streamName = this.getClass.getSimpleName,
      process = processMessage
    )

  private def processMessage(
    snsNotification: NotificationMessage): Future[Unit] = {
    debug(s"Received notification: $snsNotification")
    val eventuallyProcessedMessages = for {
      // AWS events are URL encoded, which means that the object key is URL encoded
      // The s3Client.putObject method doesn't want URL encoded keys, so decode it
      urlDecodedMessage <- Future.fromTry(
        Try(java.net.URLDecoder.decode(snsNotification.body, "utf-8")))
      eventNotification <- Future.fromTry(fromJson[S3Event](urlDecodedMessage))
      _ <- Future.sequence(
        eventNotification.Records.map { r =>
          updateRecord(r) match {
            case Right(value)       => Future.successful(value)
            case Left(storageError) => Future.failed(storageError.e)
          }
        }
      )
    } yield ()
    eventuallyProcessedMessages.failed.foreach { e: Throwable =>
      error(
        s"Error processing message. Exception ${e.getClass.getCanonicalName} ${e.getMessage}")
    }
    eventuallyProcessedMessages
  }

  private def updateRecord(
    record: S3Record): Either[StorageError, vhs.VHSEntry] = {
    val objectLocation = record.s3.objectLocation
    val id = objectLocation.key.replaceAll(".xml", "")
    val updateEventTime = record.eventTime

    for {
      stream <- s3ObjectStore.get(record.s3.objectLocation)
      result <- vhs.update(id = id)(
        ifNotExisting = (stream, GoobiRecordMetadata(updateEventTime))
      )(
        ifExisting = (existingStream, existingMetadata) => {
          if (existingMetadata.eventTime.isBefore(updateEventTime))
            (stream, GoobiRecordMetadata(updateEventTime))
          else
            (existingStream, existingMetadata)
        }
      )
    } yield result
  }
}
