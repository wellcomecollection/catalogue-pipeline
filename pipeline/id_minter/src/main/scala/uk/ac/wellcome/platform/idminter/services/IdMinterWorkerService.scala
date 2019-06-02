package uk.ac.wellcome.platform.idminter.services

import akka.Done
import io.circe.Json
import uk.ac.wellcome.messaging.BigMessageSender
import uk.ac.wellcome.messaging.message.MessageStream
import uk.ac.wellcome.platform.idminter.steps.IdEmbedder
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future
import scala.util.Try

class IdMinterWorkerService[Destination](
  idEmbedder: IdEmbedder,
  messageSender: BigMessageSender[Destination, Json],
  messageStream: MessageStream[Json]
) extends Runnable {

  def run(): Future[Done] =
    messageStream.foreach(
      this.getClass.getSimpleName,
      message => Future.fromTry { processMessage(message) }
    )

  def processMessage(json: Json): Try[Unit] =
    for {
      identifiedJson <- idEmbedder.embedId(json)
      _ <- messageSender.sendT(identifiedJson)
    } yield ()
}
