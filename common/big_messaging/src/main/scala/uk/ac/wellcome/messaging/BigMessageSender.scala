package uk.ac.wellcome.messaging

import java.text.SimpleDateFormat
import java.util.Date

import grizzled.slf4j.Logging
import io.circe.Encoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.message.{
  InlineNotification,
  MessageNotification,
  RemoteNotification
}
import uk.ac.wellcome.storage.{KeyPrefix, ObjectStore}

import scala.util.{Failure, Success, Try}

trait BigMessageSender[Destination, T] extends Logging {
  val messageSender: MessageSender[Destination]
  val objectStore: ObjectStore[T]

  val namespace: String

  implicit val encoder: Encoder[T]

  val maxMessageSize: Int

  private val dateFormat = new SimpleDateFormat("YYYY/MM/dd")

  protected def getKeyPrefix: String = {
    val currentTime = new Date()
    s"${messageSender.destination}/${dateFormat.format(currentTime)}/${currentTime.getTime.toString}"
  }

  def sendT(t: T): Try[MessageNotification] =
    for {
      jsonString <- toJson(t)
      inlineNotification = InlineNotification(jsonString)

      encodedInlineNotification <- toJson(inlineNotification)

      notification <- if (encodedInlineNotification
                            .getBytes("UTF-8")
                            .length > maxMessageSize) {
        createRemoteNotification(t)
      } else {
        Success(inlineNotification)
      }

      _ <- messageSender.sendT[MessageNotification](notification)
    } yield notification

  private def createRemoteNotification(t: T): Try[RemoteNotification] =
    (for {
      location <- objectStore.put(namespace)(
        t,
        keyPrefix = KeyPrefix(getKeyPrefix)
      )
      _ = info(s"Successfully stored message in location: $location")
      notification = RemoteNotification(location = location)
    } yield notification) match {
      case Right(value) =>
        Success(value)
      case Left(writeError) =>
        Failure(writeError.e)
    }
}
