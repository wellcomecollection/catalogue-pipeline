package weco.pipeline.transformer

import org.apache.pekko.Done
import grizzled.slf4j.Logging
import io.circe.Decoder
import io.circe.syntax._
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.source_model.SourcePayload
import weco.catalogue.internal_model.Implicits._
import weco.json.JsonUtil.fromJson
import weco.messaging.sns.NotificationMessage
import weco.pipeline_storage.Indexable._
import weco.pipeline_storage.{PipelineStorageStream, Retriever}
import weco.storage.{Identified, ReadError, Version}
import io.circe.optics.JsonPath._
import weco.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

sealed abstract class TransformerWorkerError(msg: String) extends Exception(msg)
case class DecodePayloadError[T](t: Throwable, message: NotificationMessage)
    extends TransformerWorkerError(t.getMessage)
case class StoreReadError[T](err: ReadError, key: T)
    extends TransformerWorkerError(err.toString)
case class TransformerError[SourceData, Key](
  t: Throwable,
  sourceData: SourceData,
  key: Key
) extends TransformerWorkerError(t.getMessage)

trait SourceDataRetriever[Payload, SourceData] {
  def lookupSourceData(
    p: Payload
  ): Either[ReadError, Identified[Version[String, Int], SourceData]]
}

trait TransformerEventProcessor[Payload <: SourcePayload, SourceData]
    extends Logging {
  type Result[T] = Either[TransformerWorkerError, T]
  type StoreKey = Version[String, Int]

  implicit val ec: ExecutionContext
  implicit val decoder: Decoder[Payload]

  val sourceDataRetriever: SourceDataRetriever[Payload, SourceData]
  val transformer: Transformer[SourceData]
  val transformedWorkRetriever: Retriever[Work[Source]]

  val transformerName: String

  def processEvent(
    payload: Payload
  ): Future[Result[Option[(Work[Source], StoreKey)]]] =
    Future {
      val key = Version(payload.id, payload.version)

      for {
        getResult <- getSourceData(payload)
        (sourceData, version) = getResult
        newWork <- work(sourceData, version, key)
      } yield (newWork, key)
    }.flatMap { compareToStored }

  private def compareToStored(
    workResult: Result[(Work[Source], StoreKey)]
  ): Future[Result[Option[(Work[Source], StoreKey)]]] =
    workResult match {

      // Once we've transformed the Work, we query forward -- is this a work we've
      // seen before?  If it's the same as a Work the pipeline already knows about,
      // we can skip a bunch of unnecessary processing by not sending it on.
      //
      // The pipeline is meant to be idempotent, so sending the work forward would
      // be a no-op.
      //
      // This is particularly meant to reduce the amount of work we do after the
      // nightly Sierra reharvest, when ~250k Sierra source records get synced with
      // Calm.  The records get a new modifiedDate from Sierra, but none of the data
      // we care about for the pipeline is changed.
      case Right((transformedWork, key)) =>
        transformedWorkRetriever
          .apply(workIndexable.id(transformedWork))
          .map {
            storedWork =>
              if (shouldSend(transformedWork, storedWork)) {
                Right(Some((transformedWork, key)))
              } else {
                info(
                  s"$transformerName: from $key transformed work with id ${transformedWork.id}; already in pipeline so not re-sending"
                )
                Right(None)
              }
          }
          .recover {
            case err: Throwable =>
              debug(s"Unable to retrieve work $key: $err")
              Right(Some((transformedWork, key)))
          }

      case Left(err) => Future.successful(Left(err))
    }

  private def work(
    sourceData: SourceData,
    version: Int,
    key: StoreKey
  ): Result[Work[Source]] =
    transformer(id = key.id, sourceData, version) match {
      case Right(result) => Right(result)
      case Left(err) =>
        Left(TransformerError(err, sourceData, key))
    }

  private def getSourceData(p: Payload): Result[(SourceData, Int)] =
    sourceDataRetriever
      .lookupSourceData(p)
      .map {
        case Identified(Version(storedId, storedVersion), sourceData) =>
          if (storedId != p.id) {
            warn(
              s"Stored ID ($storedId) does not match ID from message (${p.id})"
            )
          }
          debug(
            s"Retrieved sourceData version $storedVersion for key $storedId"
          )
          (sourceData, storedVersion)
      }
      .left
      .map {
        err =>
          StoreReadError(err, p)
      }

  private def shouldSend(
    transformedWork: Work[Source],
    storedWork: Work[Source]
  ): Boolean = {
    if (transformedWork.version < storedWork.version) {
      debug(
        s"${transformedWork.id}: transformed Work is older than the stored Work"
      )
      false
    }

    // We cannot guarantee that storing a work results in a message being sent to SNS.
    // If this is the same version of the Work as was previously stored, resend it to
    // ensure it gets through the pipeline.
    else if (storedWork.version == transformedWork.version) {
      debug(
        s"${transformedWork.id}: transformed Work and stored Work have the same version"
      )
      true
    }

    // Different data is always worth sending along the pipeline.
    else if (!areEquivalent(transformedWork, storedWork)) {
      debug(
        s"${transformedWork.id}: transformed Work has different data to the stored Work"
      )
      true
    }

    // If we get here, it means the new work and the stored work should have
    // the same data, but the stored work has a strictly lower version.
    //
    // Nothing in the pipeline will change if we send this work, and we
    // can assume the previous version of the work was sent successfully.
    else {
      debug(
        s"${transformedWork.id}: transformed Work has newer version/same data as the stored Work"
      )
      assert(storedWork.data == transformedWork.data)
      assert(storedWork.version < transformedWork.version)
      false
    }
  }

  private def areEquivalent(
    transformedWork: Work[Source],
    storedWork: Work[Source]
  ): Boolean = {
    // Sometimes we get updates from our sources even though the data hasn't necessarily changed.
    // One example of that is the Sierra Calm sync script that triggers an update to
    // every sierra catalogued in calm every night. It can be very expensive if we let
    // those updates travel through the whole pipeline. So, if the only change is on
    // 'version' or 'state.sourceModifiedTime' we consider the works equivalent
    val versionRemove = root.obj.modify(_.remove("version"))
    val sourceModifiedTimeRemove =
      root.state.obj.modify(_.remove("sourceModifiedTime"))
    val modifiedTransformedWork = sourceModifiedTimeRemove(
      versionRemove(transformedWork.asJson)
    )
    val modifiedSourceWork = sourceModifiedTimeRemove(
      versionRemove(storedWork.asJson)
    )

    modifiedTransformedWork == modifiedSourceWork
  }
}

/** A TransformerWorker:
  *   - Takes an SQS stream that emits VHS keys
  *   - Gets the record of type `SourceData`
  *   - Runs it through a transformer and transforms the `SourceData` to
  *     `Work[Source]`
  *   - Emits the message via `MessageSender` to SNS
  */
final class TransformerWorker[Payload <: SourcePayload, SourceData, SenderDest](
  val transformer: Transformer[SourceData],
  val transformedWorkRetriever: Retriever[Work[Source]],
  pipelineStream: PipelineStorageStream[NotificationMessage, Work[
    Source
  ], SenderDest],
  val sourceDataRetriever: SourceDataRetriever[Payload, SourceData]
)(implicit val ec: ExecutionContext, val decoder: Decoder[Payload])
    extends Logging
    with TransformerEventProcessor[Payload, SourceData]
    with Runnable {

  lazy val transformerName: String = this.getClass.getSimpleName

  def run(): Future[Done] =
    pipelineStream.foreach(
      transformerName,
      (notification: NotificationMessage) =>
        process(notification).map {
          case Left(err) =>
            // We do some slightly nicer logging here to give context to the errors
            err match {
              case DecodePayloadError(_, notificationMsg) =>
                error(
                  s"$transformerName: DecodePayloadError from $notificationMsg"
                )
              case StoreReadError(_, key) =>
                error(s"$transformerName: StoreReadError on $key")
              case TransformerError(t, sourceData, key) =>
                error(
                  s"$transformerName: TransformerError on $sourceData with $key ($t)"
                )
            }

            throw err

          case Right(None) =>
            debug(
              s"$transformerName: no transformed Work returned for $notification (this means the Work is already in the pipeline)"
            )
            Nil

          case Right(Some((work, key))) =>
            info(
              s"$transformerName: from $key transformed work with id ${work.id}"
            )
            List(work)
        }
    )

  def process(
    message: NotificationMessage
  ): Future[Result[Option[(Work[Source], StoreKey)]]] = {
    decodePayload(message).flatMap {
      payload =>
        debug(s"Decoded payload $payload, successfully")
        processEvent(payload)
    }
  }

  private def decodePayload(message: NotificationMessage): Future[Payload] =
    fromJson[Payload](message.body) match {
      case Success(storeKey) => Future.successful(storeKey)
      case Failure(err)      => Future.failed(DecodePayloadError(err, message))
    }
}
