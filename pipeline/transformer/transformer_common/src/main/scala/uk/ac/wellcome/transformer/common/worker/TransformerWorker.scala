package uk.ac.wellcome.transformer.common.worker

import scala.concurrent.Future
import akka.Done
import grizzled.slf4j.Logging
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, Version}
import WorkState.Source

sealed abstract class TransformerWorkerError(msg: String) extends Exception(msg)
case class DecodeKeyError[T](msg: String, message: NotificationMessage)
    extends TransformerWorkerError(msg)
case class StoreReadError[T](msg: String, key: T)
    extends TransformerWorkerError(msg)
case class TransformerError[In, Key](msg: String, sourceData: In, key: Key)
    extends TransformerWorkerError(msg)
case class MessageSendError[T, Key](msg: String, work: Work[Source], key: Key)
    extends TransformerWorkerError(msg)

/**
  * A TransformerWorker:
  * - Takes an SQS stream that emits VHS keys
  * - Gets the record of type `In`
  * - Runs it through a transformer and transforms the `In` to `Work[Source]`
  * - Emits the message via `BigMessageSender` to an SNS topic
  */
trait TransformerWorker[In, SenderDest] extends Logging {
  type StreamMessage = (Message, NotificationMessage)
  type Result[T] = Either[TransformerWorkerError, T]
  type StoreKey = Version[String, Int]

  def name: String = this.getClass.getSimpleName
  val stream: SQSStream[NotificationMessage]
  val sender: MessageSender[SenderDest]
  val store: VersionedStore[String, Int, In]
  val transformer: Transformer[In]
  val concurrentTransformations: Int = 2

  def process(
    message: NotificationMessage): Result[(Work[Source], StoreKey)] = {
    for {
      key <- decodeKey(message)
      recordAndKey <- getRecord(key)
      work <- work(recordAndKey._1, key)
      done <- done(work, key)
    } yield done
  }

  private def work(sourceData: In, key: StoreKey): Result[Work[Source]] =
    transformer(sourceData, key.version) match {
      case Left(err)     => Left(TransformerError(err.toString, sourceData, key))
      case Right(result) => Right(result)
    }

  private def done(work: Work[Source],
                   key: StoreKey): Result[(Work[Source], StoreKey)] =
    sender.sendT(work) toEither match {
      case Left(err) => Left(MessageSendError(err.toString, work, key))
      case Right(_)  => Right((work, key))
    }

  private def decodeKey(message: NotificationMessage): Result[StoreKey] =
    fromJson[StoreKey](message.body).toEither match {
      case Left(err)     => Left(DecodeKeyError(err.toString, message))
      case Right(result) => Right(result)
    }

  private def getRecord(key: StoreKey): Result[(In, StoreKey)] =
    store.getLatest(key.id) match {
      case Left(err)                     => Left(StoreReadError(err.toString, key))
      case Right(Identified(key, entry)) => Right((entry, key))
    }

  def run(): Future[Done] =
    stream.runStream(
      name,
      source => {
        source.mapAsync(concurrentTransformations) {
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
              case Right((work, key)) => {
                info(s"$name: from $key transformed $work")
                Future.successful(message)
              }
            }
        }
      }
    )
}
