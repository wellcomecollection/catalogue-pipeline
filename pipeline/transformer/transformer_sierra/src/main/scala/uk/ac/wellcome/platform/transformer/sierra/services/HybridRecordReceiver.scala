package uk.ac.wellcome.platform.transformer.sierra.services

import grizzled.slf4j.Logging
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import io.circe.Decoder

import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.json.JsonUtil._

import uk.ac.wellcome.bigmessaging.EmptyMetadata
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage

import uk.ac.wellcome.storage.store.{HybridStoreEntry, Store, TypedStoreEntry}
import uk.ac.wellcome.storage.{Identified, ObjectLocation, Version}

case class BackwardsCompatObjectLocation(namespace: String, key: String)

case class HybridRecord(
  id: String,
  version: Int,
  location: BackwardsCompatObjectLocation
)

abstract class HybridRecordReceiver[MsgDestination, MsgIn](
  msgSender: BigMessageSender[MsgDestination, TransformedBaseWork])(
  implicit decoder: Decoder[MsgIn])
    extends Logging {

  def receiveMessage(message: NotificationMessage,
                     transformToWork: (
                       SierraTransformable,
                       Int) => Try[TransformedBaseWork]): Future[Unit] = {
    debug(s"Starting to process message $message")

    Future.fromTry {
      for {
        msg <- fromJson[MsgIn](message.body)
        transformable <- getTransformable(msg)
        work <- transformToWork(transformable, getVersion(msg))
        msgNotification <- msgSender.sendT(work)
        _ = debug(
          s"Published work: ${work.sourceIdentifier} with message $msgNotification")
      } yield ()
    }
  }

  protected def getVersion(msg: MsgIn): Int

  protected def getTransformable(msg: MsgIn): Try[SierraTransformable]
}

class BackwardsCompatHybridRecordReceiver[MsgDestination](
  msgSender: BigMessageSender[MsgDestination, TransformedBaseWork],
  store: Store[ObjectLocation, TypedStoreEntry[SierraTransformable]])
    extends HybridRecordReceiver[MsgDestination, HybridRecord](msgSender)
    with Logging {

  protected def getVersion(record: HybridRecord): Int = record.version

  protected def getTransformable(
    record: HybridRecord): Try[SierraTransformable] =
    record match {
      case HybridRecord(_, _, BackwardsCompatObjectLocation(namespace, path)) =>
        store.get(ObjectLocation(namespace, path)) match {
          case Right(Identified(_, TypedStoreEntry(transformable, _))) =>
            Success(transformable)
          case Left(error) => Failure(error.e)
        }
    }
}

case class UpcomingMsg(id: String, version: Int)

// When the adapter has been updated to use the new storage lib we should use this
// receiver. The adapter should be changed to emit just id / version as location is
// an internal detail that should ideally be abstracted away.
class UpcomingHybridRecordReceiver[MsgDestination](
  msgSender: BigMessageSender[MsgDestination, TransformedBaseWork],
  store: Store[Version[String, Int],
               HybridStoreEntry[SierraTransformable, EmptyMetadata]])
    extends HybridRecordReceiver[MsgDestination, UpcomingMsg](msgSender)
    with Logging {

  protected def getVersion(msg: UpcomingMsg): Int = msg.version

  protected def getTransformable(msg: UpcomingMsg): Try[SierraTransformable] =
    store.get(Version(msg.id, msg.version)) match {
      case Right(Identified(_, HybridStoreEntry(transformable, _))) =>
        Success(transformable)
      case Left(error) => Failure(error.e)
    }
}
