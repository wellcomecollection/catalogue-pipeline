package uk.ac.wellcome.transformer.common.worker

import scala.concurrent.Future
import akka.Done
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.storage.{ReadError, Version}
import WorkState.Source
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream

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
  val transformer: Transformer[SourceData]

  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            SenderDest]

  protected def lookupSourceData(key: StoreKey): Either[ReadError, SourceData]

  def process(message: NotificationMessage): Result[(Work[Source], StoreKey)] =
    for {
      key <- decodeKey(message)
      record <- getRecord(key)
      work <- work(record, key)
    } yield (work, key)

  private def work(sourceData: SourceData,
                   key: StoreKey): Result[Work[Source]] =
    transformer(sourceData, key.version) match {
      case Left(err)     => Left(TransformerError(err, sourceData, key))
      case Right(result) => Right(result)
    }

  private def decodeKey(message: NotificationMessage): Result[StoreKey] =
    fromJson[StoreKey](message.body) match {
      case Failure(err)      => Left(DecodeKeyError(err, message))
      case Success(storeKey) => Right(storeKey)
    }

  private def getRecord(key: StoreKey): Result[SourceData] =
    lookupSourceData(key).left.map { err =>
      StoreReadError(err, key)
    }

  def run(): Future[Done] =
    pipelineStream.foreach(
      name,
      (notification: NotificationMessage) =>
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
            }
            Future.failed(err)
          case Right((work, key)) =>
            info(s"$name: from $key transformed $work")
            Future.successful(Some(work))
      }
    )
}
