package uk.ac.wellcome.platform.transformer.calm

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.amazonaws.services.sqs.model.Message
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.calm.models.CalmRecord
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}

sealed trait CalmWorkerError extends Throwable
object DecodeKeyError extends CalmWorkerError
object StoreReadError extends CalmWorkerError
object TransformerError extends CalmWorkerError
object MessageSendError extends CalmWorkerError

class CalmWorker[SenderDest](
  sender: BigMessageSender[SenderDest, TransformedBaseWork],
  store: VersionedStore[String, Int, CalmRecord],
  source: Source[(Message, NotificationMessage), NotUsed])
    extends Worker[
      (Message, NotificationMessage),
      CalmRecord,
      TransformedBaseWork] {
  type Result[T] = Either[Throwable, T]
  type StoreKey = Version[String, Int]

  def decodeMessage(
    message: (Message, NotificationMessage)): Result[CalmRecord] =
    decodeKey(message._2) flatMap getRecord

  def work(sourceData: CalmRecord): Result[TransformedBaseWork] =
    CalmTransformer.transform(sourceData) match {
      case Left(_)       => Left(TransformerError)
      case Right(result) => Right(result)
    }

  def done(work: TransformedBaseWork): Result[Unit] =
    sender.sendT(work) toEither match {
      case Left(_)  => Left(MessageSendError)
      case Right(_) => Right()
    }

  private def decodeKey(message: NotificationMessage) =
    fromJson[StoreKey](message.body).toEither match {
      case Left(_)       => Left(DecodeKeyError)
      case Right(result) => Right(result)
    }

  private def getRecord(key: StoreKey) = store.get(key) match {
    case Left(_)                     => Left(StoreReadError)
    case Right(Identified(_, entry)) => Right(entry)
  }
}
