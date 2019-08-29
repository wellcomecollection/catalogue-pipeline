package uk.ac.wellcome.bigmessaging

import grizzled.slf4j.Logging
import io.circe.Decoder
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.bigmessaging.message.{
  InlineNotification,
  MessageNotification,
  RemoteNotification
}
import uk.ac.wellcome.storage.{Identified, NotFoundError, ObjectLocation}
import uk.ac.wellcome.storage.store.{TypedStore, TypedStoreEntry}

import scala.util.{Failure, Success, Try}

trait BigMessageReader[T] extends Logging {
  val typedStore: TypedStore[ObjectLocation, T]

  implicit val decoder: Decoder[T]

  // TODO: Turn this into an Either[ReadError, T] to match the underlying pattern in TypedStore
  // See: https://github.com/wellcometrust/scala-storage/blob/master/storage/src/main/scala/uk/ac/wellcome/storage/store/Store.scala#L8
  def read(notification: MessageNotification): Try[T] =
    notification match {
      case inlineNotification: InlineNotification =>
        fromJson[T](inlineNotification.jsonString)

      case remoteNotification: RemoteNotification => {
        typedStore.get(remoteNotification.location) match {
          case Right(Identified(_, TypedStoreEntry(value, _))) =>
            Success(value)
          case Left(_: NotFoundError) =>
            Failure(new Exception(s"Nothing at ${remoteNotification.location}"))
          case Left(readError) =>
            Failure(readError.e)
        }
      }
    }
}
