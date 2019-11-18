package uk.ac.wellcome.platform.transformer.mets.service

import scala.concurrent.Future
import akka.Done

import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._

case class MetsData(path: String, version: Int)

class MetsTransformerWorkerService[MsgDestination](
  msgStream: SQSStream[MetsData],
  msgSender: MessageSender[MsgDestination])
    extends Runnable {

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.foreach(
      className,
      _ => Future.successful(())
    )
}
