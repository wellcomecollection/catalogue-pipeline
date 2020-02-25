package uk.ac.wellcome.platform.transformer.calm.service

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import akka.{Done, NotUsed}
import com.amazonaws.services.sqs.model.Message
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.calm.{CalmTransformer, Worker}
import uk.ac.wellcome.platform.transformer.calm.models.CalmRecord
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

sealed trait CalmTransformerWorkerError extends Throwable
object DecodeKeyError extends CalmTransformerWorkerError
object StoreReadError extends CalmTransformerWorkerError
object TransformerError extends CalmTransformerWorkerError
object MessageSendError extends CalmTransformerWorkerError

class CalmTransformerWorkerService(
  stream: SQSStream[NotificationMessage],
  messageSender: BigMessageSender[SNSConfig, TransformedBaseWork],
  adapterStore: VersionedStore[String, Int, CalmRecord],
  source: Source[(Message, NotificationMessage), NotUsed]
)(implicit
  val ec: ExecutionContext,
  materializer: ActorMaterializer)
    extends Worker[
      (Message, NotificationMessage),
      CalmRecord,
      TransformedBaseWork]
    with Runnable {
  type Result[T] = Either[Throwable, T]
  type StoreKey = Version[String, Int]

  def run(): Future[Done] =
    stream.runStream(
      "CalmTransformerWorkerService",
      source => {
        val end = source.via(Flow.fromFunction(message => message._1))
        val processed = processMessage(source)

        processed.flatMapConcat(_ => end)
      }
    )

  def decodeMessage(
    message: (Message, NotificationMessage)): Result[CalmRecord] =
    decodeKey(message._2) flatMap getRecord

  def work(sourceData: CalmRecord): Result[TransformedBaseWork] =
    CalmTransformer.transform(sourceData) match {
      case Left(_)       => Left(TransformerError)
      case Right(result) => Right(result)
    }

  def done(work: TransformedBaseWork) =
    messageSender.sendT(work) toEither match {
      case Left(_)  => Left(MessageSendError)
      case Right(_) => Right()
    }

  private def decodeKey(message: NotificationMessage) =
    fromJson[StoreKey](message.body).toEither match {
      case Left(_)       => Left(DecodeKeyError)
      case Right(result) => Right(result)
    }

  private def getRecord(key: StoreKey) = adapterStore.get(key) match {
    case Left(_)                     => Left(StoreReadError)
    case Right(Identified(_, entry)) => Right(entry)
  }
}
