package weco.catalogue.transformer

import akka.Done
import grizzled.slf4j.Logging
import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.pipeline_storage.Indexable._
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import weco.catalogue
import weco.catalogue.source_model.SourcePayload

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

sealed abstract class TransformerWorkerError(msg: String) extends Exception(msg)
case class DecodePayloadError[T](t: Throwable, message: NotificationMessage)
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
trait TransformerWorker[Payload <: SourcePayload, SourceData, SenderDest]
    extends Logging {
  type Result[T] = Either[TransformerWorkerError, T]
  type StoreKey = Version[String, Int]

  implicit val ec: ExecutionContext

  def name: String = this.getClass.getSimpleName
  val transformer: Transformer[SourceData]

  val retriever: Retriever[Work[Source]]

  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            SenderDest]

  def lookupSourceData(
    p: Payload): Either[ReadError, Identified[Version[String, Int], SourceData]]

  implicit val decoder: Decoder[Payload]

  def process(message: NotificationMessage)
    : Future[Result[Option[(Work[Source], StoreKey)]]] =
    Future {
      for {
        payload <- decodePayload(message)
        key = Version(payload.id, payload.version)
        recordResult <- getRecord(payload)
        (record, version) = recordResult
        newWork <- work(record, version, key)
      } yield (newWork, key)
    }.flatMap { compareToStored }

  private def work(sourceData: SourceData,
                   version: Int,
                   key: StoreKey): Result[Work[Source]] =
    transformer(sourceData, version) match {
      case Right(result) => Right(result)
      case Left(err) =>
        Left(catalogue.transformer.TransformerError(err, sourceData, key))
    }

  private def decodePayload(message: NotificationMessage): Result[Payload] =
    fromJson[Payload](message.body) match {
      case Success(storeKey) => Right(storeKey)
      case Failure(err)      => Left(DecodePayloadError(err, message))
    }

  private def getRecord(p: Payload): Result[(SourceData, Int)] =
    lookupSourceData(p)
      .map {
        case Identified(Version(storedId, storedVersion), sourceData) =>
          if (storedId != p.id) {
            warn(
              s"Stored ID ($storedId) does not match ID from message (${p.id})")
          }

          (sourceData, storedVersion)
      }
      .left
      .map { err =>
        StoreReadError(err, p)
      }

  import WorkComparison._

  private def compareToStored(workResult: Result[(Work[Source], StoreKey)])
    : Future[Result[Option[(Work[Source], StoreKey)]]] =
    workResult match {

      // Once we've transformed the Work, we query forward -- is this a work we've
      // seen before?  If it's a Work the pipeline already knows about, we can skip
      // a bunch of unnecessary processing by not sending it on.
      //
      // The pipeline is meant to be idempotent, so sending the work forward would
      // be a no-op.
      //
      // This is particularly meant to reduce the amount of work we do after the
      // nightly Sierra reharvest, when ~250k Sierra source records get synced with
      // Calm.  The records get a new modifiedDate from Sierra, but none of the data
      // we care about for the pipeline is changed.
      case Right((newWork, key)) =>
        retriever
          .apply(workIndexable.id(newWork))
          .map { storedWork =>
            if (newWork.shouldReplace(storedWork)) {
              Right(Some((newWork, key)))
            } else {
              info(
                s"$name: from $key transformed work; already in pipeline so not re-sending")
              Right(None)
            }
          }
          .recover {
            case err: Throwable =>
              debug(s"Unable to retrieve work $key: $err")
              Right(Some((newWork, key)))
          }

      case Left(err) => Future.successful(Left(err))
    }

  def run(): Future[Done] =
    pipelineStream.foreach(
      name,
      (notification: NotificationMessage) =>
        process(notification).map {
          case Left(err) =>
            // We do some slightly nicer logging here to give context to the errors
            err match {
              case DecodePayloadError(_, notificationMsg) =>
                error(s"$name: DecodePayloadError from $notificationMsg")
              case StoreReadError(_, key) =>
                error(s"$name: StoreReadError on $key")
              case TransformerError(_, sourceData, key) =>
                error(s"$name: TransformerError on $sourceData with $key")
            }

            throw err

          case Right(None) =>
            debug(
              s"$name: no transformed Work returned for $notification (this means the Work is already in the pipeline)")
            None

          case Right(Some((work, key))) =>
            info(s"$name: from $key transformed work with id ${work.id}")
            Some(work)
      }
    )
}
