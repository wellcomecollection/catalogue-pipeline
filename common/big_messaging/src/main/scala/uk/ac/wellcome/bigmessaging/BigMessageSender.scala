package uk.ac.wellcome.bigmessaging

import java.text.SimpleDateFormat
import java.util.Date

import grizzled.slf4j.Logging
import io.circe.Encoder
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.bigmessaging.message.{
  InlineNotification,
  MessageNotification,
  RemoteNotification
}
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.Store

import scala.util.{Failure, Success, Try}

trait BigMessageSender[Destination, T] extends Logging {
  val messageSender: MessageSender[Destination]
  val store: Store[ObjectLocation, T]

  val namespace: String

  implicit val encoder: Encoder[T]

  val maxMessageSize: Int

  private val dateFormat = new SimpleDateFormat("YYYY/MM/dd")

  protected def getKey: String = {
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

  private def createRemoteNotification(t: T): Try[RemoteNotification] = {
    val id = ObjectLocation(namespace, getKey)
    (for {
      location <- store.put(id)(t)
      _ = info(s"Successfully stored message in location: ${location.id}")
      notification = RemoteNotification(id)
    } yield notification) match {
      case Right(value) =>
        Success(value)
      case Left(writeError) =>
        Failure(writeError.e)
    }
  }
}
