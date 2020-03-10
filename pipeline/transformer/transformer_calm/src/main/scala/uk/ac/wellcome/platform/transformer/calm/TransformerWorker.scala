package uk.ac.wellcome.platform.transformer.calm

import akka.Done
import com.amazonaws.services.sqs.model.Message
import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}

import scala.concurrent.Future

sealed abstract class TransformerWorkerError(msg: String) extends Exception(msg)
case class DecodeKeyError[T](msg: String, message: NotificationMessage)
    extends TransformerWorkerError(msg)
case class StoreReadError[T](msg: String, key: T)
    extends TransformerWorkerError(msg)
case class TransformerError[In, Key](msg: String, sourceData: In, key: Key)
    extends TransformerWorkerError(msg)
case class MessageSendError[T, Key](msg: String,
                                    work: TransformedBaseWork,
                                    key: Key)
    extends TransformerWorkerError(msg)

/**
  * A TransformerWorker:
  * - Takes an SQS stream that emits VHS keys
  * - Gets the record of type `In`
  * - Runs it through a transformer and transforms the `In` to `TransformedBaseWork`
  * - Emits the message via `BigMessageSender` to an SNS topic
  */
trait TransformerWorker[In, SenderDest] extends Logging {
  type StreamMessage = (Message, NotificationMessage)
  type Result[T] = Either[TransformerWorkerError, T]
  type StoreKey = Version[String, Int]

  def name: String = this.getClass.getSimpleName
  val stream: SQSStream[NotificationMessage]
  val sender: BigMessageSender[SenderDest, TransformedBaseWork]
  val store: VersionedStore[String, Int, In]
  val transformer: Transformer[In]

  def process(message: NotificationMessage): Result[Unit] = {
    for {
      key <- decodeKey(message)
      recordAndKey <- getRecord(key)
      work <- work(recordAndKey._1, key)
      done <- done(work, key)
    } yield done
  }

  private def work(sourceData: In, key: StoreKey): Result[TransformedBaseWork] =
    transformer(sourceData, key.version) match {
      case Left(err)     => Left(TransformerError(err.toString, sourceData, key))
      case Right(result) => Right(result)
    }

  private def done(work: TransformedBaseWork, key: StoreKey): Result[Unit] =
    sender.sendT(work) toEither match {
      case Left(err) => Left(MessageSendError(err.toString, work, key))
      case Right(_)  => Right((): Unit)
    }

  private def decodeKey(message: NotificationMessage): Result[StoreKey] =
    fromJson[StoreKey](message.body).toEither match {
      case Left(err)     => Left(DecodeKeyError(err.toString, message))
      case Right(result) => Right(result)
    }

  private def getRecord(key: StoreKey): Result[(In, StoreKey)] =
    store.get(key) match {
      case Left(err)                     => Left(StoreReadError(err.toString, key))
      case Right(Identified(key, entry)) => Right((entry, key))
    }

  def run(): Future[Done] =
    stream.runStream(
      name,
      source => {
        source.mapAsync(2) {
          case (message, notification) =>
            process(notification) match {
              case Left(err) => {
                // We do some slightly nicer logging here to give context to the errors
                err match {
                  case DecodeKeyError(_, message) =>
                    error(s"$name: DecodeKeyError from $message")
                  case StoreReadError(_, key) =>
                    error(s"$name: StoreReadError on $key")
                  case TransformerError(_, sourceData, key) =>
                    error(s"$name: TransformerError on $sourceData with $key")
                  case MessageSendError(_, work, key) =>
                    error(s"$name: MessageSendError on $work with $key")

                }
                Future.failed(err)
              }
              case Right(_) => Future.successful(message)
            }
        }
      }
    )
}
