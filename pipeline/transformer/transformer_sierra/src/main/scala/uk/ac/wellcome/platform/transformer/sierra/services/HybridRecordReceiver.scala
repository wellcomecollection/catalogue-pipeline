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

import uk.ac.wellcome.storage.store.{HybridStoreEntry, VersionedStore, TypedStore, TypedStoreEntry}
import uk.ac.wellcome.storage.{Identified, Version, ObjectLocation}

case class BackwardsCompatObjectLocation(namespace: String, key: String)

case class HybridRecord[Location](
  id: String,
  version: Int,
  location: Location
)

abstract class HybridRecordReceiver[MsgDestination, Location](
  msgSender: BigMessageSender[MsgDestination, TransformedBaseWork])(
  implicit decoder: Decoder[HybridRecord[Location]]) extends Logging {

  def receiveMessage(message: NotificationMessage,
                     transformToWork: (
                       SierraTransformable,
                       Int) => Try[TransformedBaseWork]): Future[Unit] = {
    debug(s"Starting to process message $message")

    Future.fromTry { for {
        record <- fromJson[HybridRecord[Location]](message.body)
        transformable <- getTransformable(record)
        work <- transformToWork(transformable, record.version)
        msgNotification <- msgSender.sendT(work)
        _ = debug(
          s"Published work: ${work.sourceIdentifier} with message $msgNotification")
      } yield ()
    }
  }

  protected def getTransformable(record: HybridRecord[Location]): Try[SierraTransformable]
}

class BackwardsCompatHybridRecordReceiver[MsgDestination](
  msgSender: BigMessageSender[MsgDestination, TransformedBaseWork],
  store: TypedStore[ObjectLocation, SierraTransformable])
    extends HybridRecordReceiver[MsgDestination, BackwardsCompatObjectLocation](msgSender) with Logging {

  protected def getTransformable(record: HybridRecord[BackwardsCompatObjectLocation]): Try[SierraTransformable] =
    record match {
      case HybridRecord(_, _, BackwardsCompatObjectLocation(namespace, path)) =>
        store.get(ObjectLocation(namespace, path)) match {
          case Right(Identified(_, TypedStoreEntry(transformable, _))) =>
            Success(transformable)
          case Left(error) => Failure(error.e)
        }
    }
}

class UpcomingHybridRecordReceiver[MsgDestination](
  msgSender: BigMessageSender[MsgDestination, TransformedBaseWork],
  store: VersionedStore[String,
                        Int,
                        HybridStoreEntry[SierraTransformable, EmptyMetadata]])
    extends HybridRecordReceiver[MsgDestination, ObjectLocation](msgSender) with Logging {

  protected def getTransformable(record: HybridRecord[ObjectLocation]): Try[SierraTransformable] =
    record match {
      case HybridRecord(id, version, _) =>
        store.get(Version(id, version)) match {
          case Right(Identified(_, HybridStoreEntry(transformable, _))) =>
            Success(transformable)
          case Left(error) => Failure(error.e)
        }
    }
}
