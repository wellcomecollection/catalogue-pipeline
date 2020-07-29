package uk.ac.wellcome.bigmessaging

import java.text.SimpleDateFormat
import java.util.Date

import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.message.{
  InlineNotification,
  MessageNotification,
  RemoteNotification
}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.IndividualMessageSender
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.Store

import scala.util.{Failure, Success, Try}

trait IndividualBigMessageSender[Destination]
    extends IndividualMessageSender[Destination]
    with Logging {
  val underlying: IndividualMessageSender[Destination]
  val store: Store[ObjectLocation, String]
  val maxMessageSize: Int
  val namespace: String

  override def send(body: String)(subject: String,
                                  destination: Destination): Try[Unit] = {
    val inlineNotification = InlineNotification(body)

    for {
      encodedInlineNotification <- toJson(inlineNotification)

      encodedLength = encodedInlineNotification.getBytes("UTF-8").length

      _ = debug(s"Length of encoded notification: $encodedLength")

      notification <- if (encodedLength > maxMessageSize) {
        debug(s"Message is longer than $maxMessageSize; sending a remote notification")
        createRemoteNotification(body, destination)
      } else {
        debug(s"Message is shorter than $maxMessageSize; sending an inline notification")
        Success(inlineNotification)
      }

      _ <- underlying.sendT[MessageNotification](notification)(
        subject,
        destination)
    } yield ()
  }

  private val dateFormat = new SimpleDateFormat("YYYY/MM/dd")

  private def getKey(destination: Destination): String = {
    val currentTime = new Date()
    s"$destination/${dateFormat.format(currentTime)}/${currentTime.getTime.toString}"
  }

  private def createRemoteNotification(
    body: String,
    destination: Destination): Try[RemoteNotification] = {
    val location = ObjectLocation(namespace, getKey(destination))

    val notification =
      for {
        _ <- store.put(location)(body)
        _ = info(s"Successfully stored message in location: $location")
        notification = RemoteNotification(location)
      } yield notification

    notification match {
      case Right(value)     => Success(value)
      case Left(writeError) => Failure(writeError.e)
    }
  }
}
