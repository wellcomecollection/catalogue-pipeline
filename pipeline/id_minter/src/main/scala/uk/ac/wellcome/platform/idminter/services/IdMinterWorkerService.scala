package uk.ac.wellcome.platform.idminter.services

import akka.Done
import io.circe.Json
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.platform.idminter.steps.IdEmbedder
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class IdMinterWorkerService[StoreType <: Store[SourceIdentifier, Identifier], Destination](
  idEmbedder: IdEmbedder[StoreType],
  sender: BigMessageSender[Destination, Json],
  messageStream: BigMessageStream[Json],
)(implicit ec: ExecutionContext)
    extends Runnable {

  def run(): Future[Done] =
    messageStream.foreach(this.getClass.getSimpleName, processMessage)

  def processMessage(json: Json): Future[Unit] =
    for {
      identifiedJson <- idEmbedder.embedId(json)
      _ <- Future.fromTry { sender.sendT(identifiedJson) }
    } yield ()
}
