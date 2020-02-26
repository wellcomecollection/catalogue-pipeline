package uk.ac.wellcome.platform.transformer.calm

import akka.stream.scaladsl.Flow
import com.amazonaws.services.sqs.model.Message
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.calm.models.CalmRecord
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}

sealed abstract class CalmWorkerError(msg: String) extends Exception(msg)
case class DecodeKeyError(msg: String) extends CalmWorkerError(msg)
case class StoreReadError(msg: String) extends CalmWorkerError(msg)
case class TransformerError(msg: String) extends CalmWorkerError(msg)
case class MessageSendError(msg: String) extends CalmWorkerError(msg)

class CalmTransformerWorker[SenderDest](
  sender: BigMessageSender[SenderDest, TransformedBaseWork],
  store: VersionedStore[String, Int, CalmRecord])
    extends StreamWorker[
      (Message, NotificationMessage),
      CalmRecord,
      TransformedBaseWork] {

  type Result[T] = Either[Throwable, T]
  type StoreKey = Version[String, Int]

  lazy val decodeMessage =
    Flow.fromFunction(message => decodeKey(message._2) flatMap getRecord)

  lazy val work = Flow.fromFunction(sourceData =>
    CalmTransformer.transform(sourceData) match {
      case Left(err)     => Left(TransformerError(err.toString))
      case Right(result) => Right(result)
  })

  lazy val done = Flow.fromFunction(work =>
    sender.sendT(work) toEither match {
      case Left(err) => Left(MessageSendError(err.toString))
      case Right(_)  => Right((): Unit)
  })

  private def decodeKey(message: NotificationMessage) =
    fromJson[StoreKey](message.body).toEither match {
      case Left(err)     => Left(DecodeKeyError(err.toString))
      case Right(result) => Right(result)
    }

  private def getRecord(key: StoreKey) = store.get(key) match {
    case Left(err)                   => Left(StoreReadError(err.toString))
    case Right(Identified(_, entry)) => Right(entry)
  }
}
