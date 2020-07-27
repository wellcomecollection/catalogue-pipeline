package uk.ac.wellcome.bigmessaging

import grizzled.slf4j.Logging
import io.circe.Decoder
import uk.ac.wellcome.bigmessaging.message.{
  InlineNotification,
  MessageNotification,
  RemoteNotification
}
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.{Identified, NotFoundError}

import scala.util.{Failure, Success, Try}

trait BigMessageReader[Location, T] extends Logging {
  val store: Store[Location, T]

  implicit val decoder: Decoder[T]

  // TODO: Turn this into an Either[ReadError, T] to match the underlying pattern in Store
  def read(notification: MessageNotification): Try[T] =
    notification match {
      case inlineNotification: InlineNotification =>
        fromJson[T](inlineNotification.jsonString)

      case remoteNotification: RemoteNotification[Location] => {
        store.get(remoteNotification.location) match {
          case Right(Identified(_, value)) =>
            Success(value)
          case Left(_: NotFoundError) =>
            Failure(new Exception(s"Nothing at ${remoteNotification.location}"))
          case Left(readError) =>
            Failure(readError.e)
        }
      }
    }
}
