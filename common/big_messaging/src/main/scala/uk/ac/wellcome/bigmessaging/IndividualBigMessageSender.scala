package uk.ac.wellcome.bigmessaging

import java.text.SimpleDateFormat
import java.util.Date

import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.message.{InlineNotification, MessageNotification, RemoteNotification}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.IndividualMessageSender
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.Store

import scala.util.{Failure, Success, Try}

trait IndividualBigMessageSender[Destination] extends IndividualMessageSender[Destination] with Logging {
  val underlying: IndividualMessageSender[Destination]
  val store: Store[ObjectLocation, String]
  val maxMessageSize: Int
  val namespace: String

  override def send(body: String)(subject: String, destination: Destination): Try[Unit] = {
    val inlineNotification = InlineNotification(body)

    for {
      encodedInlineNotification <- toJson(inlineNotification)

      notification <- if (encodedInlineNotification
        .getBytes("UTF-8")
        .length > maxMessageSize) {
        createRemoteNotification(body)
      } else {
        Success(inlineNotification)
      }

      _ <- underlying.sendT[MessageNotification](notification)
    } yield notification
  }

  private val dateFormat = new SimpleDateFormat("YYYY/MM/dd")

  protected def getKey: String = {
    val currentTime = new Date()
    s"${underlying.destination}/${dateFormat.format(currentTime)}/${currentTime.getTime.toString}"
  }

  private def createRemoteNotification(body: String): Try[RemoteNotification] = {
    val location = ObjectLocation(namespace, getKey)

    val notification =
      for {
        _ <- store.put(location)(body)
        _ = info(s"Successfully stored message in location: $location")
        notification = RemoteNotification(location)
      } yield notification

    notification match {
      case Right(value) => Success(value)
      case Left(writeError) => Failure(writeError.e)
    }
  }
}
