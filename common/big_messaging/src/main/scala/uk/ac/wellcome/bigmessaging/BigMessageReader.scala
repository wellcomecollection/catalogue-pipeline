package uk.ac.wellcome.bigmessaging

import grizzled.slf4j.Logging
import io.circe.Decoder
import uk.ac.wellcome.bigmessaging.message.{
  InlineNotification,
  MessageNotification,
  RemoteNotification
}
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.bigmessaging.message.{
  InlineNotification,
  RemoteNotification
}
import uk.ac.wellcome.storage.ObjectStore

import scala.util.{Failure, Success, Try}

trait BigMessageReader[T] extends Logging {
  val objectStore: ObjectStore[T]

  implicit val decoder: Decoder[T]

  def read(notification: MessageNotification): Try[T] =
    notification match {
      case inlineNotification: InlineNotification =>
        fromJson[T](inlineNotification.jsonString)
      case remoteNotification: RemoteNotification =>
        objectStore.get(remoteNotification.location) match {
          case Right(value) =>
            Success(value)
          case Left(readError) =>
            Failure(readError.e)
        }
    }
}
