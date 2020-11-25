package uk.ac.wellcome.transformer.common.worker

import scala.concurrent.Future
import akka.Done
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import WorkState.Source

import scala.util.{Failure, Success}

sealed abstract class TransformerWorkerError(msg: String) extends Exception(msg)
case class DecodeKeyError[T](t: Throwable, message: NotificationMessage)
    extends TransformerWorkerError(t.getMessage)
case class StoreReadError[T](err: ReadError, key: T)
    extends TransformerWorkerError(err.toString)
case class TransformerError[SourceData, Key](t: Throwable,
                                             sourceData: SourceData,
                                             key: Key)
    extends TransformerWorkerError(t.getMessage)
case class MessageSendError[T, Key](t: Throwable, work: Work[Source], key: Key)
    extends TransformerWorkerError(t.getMessage)

/**
  * A TransformerWorker:
  * - Takes an SQS stream that emits VHS keys
  * - Gets the record of type `SourceData`
  * - Runs it through a transformer and transforms the `SourceData` to `Work[Source]`
  * - Emits the message via `MessageSender` to SNS
  */
trait TransformerWorker[SourceData, SenderDest] extends Logging {
  type Result[T] = Either[TransformerWorkerError, T]
  type StoreKey = Version[String, Int]

  def name: String = this.getClass.getSimpleName
  val stream: SQSStream[NotificationMessage]
  val sender: MessageSender[SenderDest]
  val store: VersionedStore[String, Int, SourceData]
  val transformer: Transformer[SourceData]
  val concurrentTransformations: Int = 2

  def process(message: NotificationMessage): Result[(Work[Source], StoreKey)] =
    for {
      key <- decodeKey(message)
      record <- getRecord(key)
      work <- work(record, key)
      done <- done(work, key)
    } yield done

  private def work(sourceData: SourceData,
                   key: StoreKey): Result[Work[Source]] =
    transformer(sourceData, key.version) match {
      case Left(err)     => Left(TransformerError(err, sourceData, key))
      case Right(result) => Right(result)
    }

  private def done(work: Work[Source],
                   key: StoreKey): Result[(Work[Source], StoreKey)] =
    sender.sendT(work) match {
      case Failure(err) => Left(MessageSendError(err, work, key))
      case Success(_)   => Right((work, key))
    }

  private def decodeKey(message: NotificationMessage): Result[StoreKey] =
    fromJson[StoreKey](message.body) match {
      case Failure(err)      => Left(DecodeKeyError(err, message))
      case Success(storeKey) => Right(storeKey)
    }

  private def getRecord(key: StoreKey): Result[SourceData] =
    store.getLatest(key.id) match {
      case Left(err)                   => Left(StoreReadError(err, key))
      case Right(Identified(_, entry)) => Right(entry)
    }

  def run(): Future[Done] =
    stream.runStream(
      name,
      source => {
        source.mapAsync(concurrentTransformations) {
          case (message, notification) =>
            process(notification) match {
              case Left(err) =>
                // We do some slightly nicer logging here to give context to the errors
                err match {
                  case DecodeKeyError(_, notificationMsg) =>
                    error(s"$name: DecodeKeyError from $notificationMsg")
                  case StoreReadError(_, key) =>
                    error(s"$name: StoreReadError on $key")
                  case TransformerError(_, sourceData, key) =>
                    error(s"$name: TransformerError on $sourceData with $key")
                  case MessageSendError(_, work, key) =>
                    error(s"$name: MessageSendError on $work with $key")
                }
                Future.failed(err)
              case Right((work, key)) =>
                info(s"$name: from $key transformed $work")
                Future.successful(message)
            }
        }
      }
    )
}
