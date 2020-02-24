package uk.ac.wellcome.platform.transformer.calm.service

import akka.Done
import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.{
  TransformedBaseWork,
  UnidentifiedWork,
  WorkData
}
import uk.ac.wellcome.platform.transformer.calm.{
  CalmTransformer,
  CalmTransformerError,
  Transformer
}
import uk.ac.wellcome.platform.transformer.calm.models.CalmRecord
import uk.ac.wellcome.platform.transformer.calm.transformers.CalmToSourceIdentifier
import uk.ac.wellcome.storage.store.{Readable, VersionedStore}
import uk.ac.wellcome.storage.{Identified, ObjectLocation, ReadError, Version}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

sealed trait CalmTransformerWorkerServiceState {
  val err: Throwable
}
case class MessageReadError(err: Throwable, message: NotificationMessage)
    extends CalmTransformerWorkerServiceState
case class CalmStoreReadError(err: Throwable, entry: Version[String, Int])
    extends CalmTransformerWorkerServiceState
case class CalmTransformationError(err: CalmTransformerError, data: CalmRecord)
    extends CalmTransformerWorkerServiceState
case class MessageSendError(err: Throwable)
    extends CalmTransformerWorkerServiceState

class CalmTransformerWorkerService(
  msgStream: SQSStream[NotificationMessage],
  messageSender: BigMessageSender[SNSConfig, TransformedBaseWork],
  adapterStore: VersionedStore[String, Int, CalmRecord],
  calmStore: Readable[ObjectLocation, String]
) extends Runnable
    with Logging {

  type Result[T] = Either[CalmTransformerWorkerServiceState, T]

  def run(): Future[Done] =
    msgStream.foreach(this.getClass.getSimpleName, processAndLog)

  def processAndLog(message: NotificationMessage): Future[Unit] = {
    val tried = for {
      key <- readMessage(message)
      calmRecord <- getCalmRecord(key)
      work <- transform(calmRecord)
      sent <- sendSuccessfulTransformation(work)
    } yield sent

    tried fold ({
      case MessageReadError(err, message) => error("MessageReadError")
      case CalmStoreReadError(err, entry) => error("CalmStoreReadError")
      case CalmTransformationError(err, data) =>
        error("CalmTransformationError")
      case MessageSendError(err) => error("MessageSendError")
    },
    _ => info("Success"))

    tried match {
      case Left(transformerError) => Future.failed(transformerError.err)
      case Right(_)               => Future.successful()
    }
  }

  private def readMessage(
    message: NotificationMessage): Result[Version[String, Int]] =
    fromJson[Version[String, Int]](message.body).toEither match {
      case Left(err) => Left(MessageReadError(err, message))
      case _         => _
    }

  private def getCalmRecord(key: Version[String, Int]): Result[CalmRecord] =
    adapterStore.get(key) match {
      case Left(err)                   => Left(CalmStoreReadError(err.e, key))
      case Right(Identified(_, entry)) => Right(entry)
    }

  private def transform(calmRecord: CalmRecord): Result[TransformedBaseWork] =
    CalmTransformer.transform(calmRecord) match {
      case Left(err) => Left(CalmTransformationError(err, calmRecord))
      case _         => _
    }

  private def sendSuccessfulTransformation(
    work: TransformedBaseWork): Result[Unit] = {
    messageSender.sendT(work) toEither match {
      case Left(err) => Left(MessageSendError(err))
      case Right(_)  => Right()
    }
  }
}
