package uk.ac.wellcome.platform.transformer.mets.service

import akka.Done
import uk.ac.wellcome.bigmessaging.message.BigMessageStream
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.platform.transformer.mets.model.IngestUpdate
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class MetsTransformerWorkerService[MsgDestination](messageStream: BigMessageStream[IngestUpdate],
                                             msgSender: MessageSender[MsgDestination]) extends Runnable {
  def run(): Future[Done] =
    messageStream.foreach(this.getClass.getSimpleName, (_: IngestUpdate) => Future.successful(()))

}


