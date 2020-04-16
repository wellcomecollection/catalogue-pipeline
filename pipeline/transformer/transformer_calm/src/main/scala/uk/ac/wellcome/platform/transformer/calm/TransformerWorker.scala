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

  sealed trait TransformResult;
  case class Success(key: StoreKey, work: TransformedBaseWork)
      extends TransformResult
  case class Surpress(key: StoreKey) extends TransformResult
  case class Error(error: TransformerWorkerError) extends TransformResult

  def name: String = this.getClass.getSimpleName
  val stream: SQSStream[NotificationMessage]
  val sender: BigMessageSender[SenderDest, TransformedBaseWork]
  val store: VersionedStore[String, Int, In]
  val transformer: Transformer[In]
  val concurrentTransformations: Int = 2

  def process(message: NotificationMessage): TransformResult =
    (for {
      key <- decodeKey(message)
      record <- getRecord(key)
      shouldTransform = transformer.shouldTransform(record)
      result <- if (!shouldTransform) Right(Surpress(key))
      else
        for {
          success <- work(record, key)
          _ <- done(success)
        } yield success
    } yield result) match {
      case Left(err)                => Error(err)
      case Right(successOrSurpress) => successOrSurpress
    }

  private def work(sourceData: In, key: StoreKey): Result[Success] =
    transformer(sourceData, key.version) match {
      case Left(err)   => Left(TransformerError(err.toString, sourceData, key))
      case Right(work) => Right(Success(key, work))
    }

  private def done(success: Success): Result[Unit] =
    sender.sendT(success.work) toEither match {
      case Left(err) =>
        Left(MessageSendError(err.toString, success.work, success.key))
      case Right(_) => Right(())
    }

  private def decodeKey(message: NotificationMessage): Result[StoreKey] =
    fromJson[StoreKey](message.body).toEither match {
      case Left(err)     => Left(DecodeKeyError(err.toString, message))
      case Right(result) => Right(result)
    }

  private def getRecord(key: StoreKey): Result[In] =
    store.getLatest(key.id) match {
      case Left(err)                   => Left(StoreReadError(err.toString, key))
      case Right(Identified(_, entry)) => Right(entry)
    }

  def run(): Future[Done] =
    stream.runStream(
      name,
      source => {
        source.mapAsync(concurrentTransformations) {
          case (message, notification) =>
            process(notification) match {
              case Error(err) => {
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
              case Success(key, work) => {
                info(s"$name: from $key transformed $work")
                Future.successful(message)
              }
              case Surpress(key) => {
                info(s"$name: surpressed $key")
                Future.successful(message)
              }
            }
        }
      }
    )
}
