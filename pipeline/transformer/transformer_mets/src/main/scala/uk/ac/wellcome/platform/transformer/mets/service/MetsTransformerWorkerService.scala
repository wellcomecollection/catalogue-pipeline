package uk.ac.wellcome.platform.transformer.mets.service

import akka.Done
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

case class MetsData(path: String, version: Int)

class MetsTransformerWorkerService(
                                    msgStream: SQSStream[MetsData],
                                    messageSender: BigMessageSender[SNSConfig,TransformedBaseWork])
    extends Runnable {

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.foreach(
      className,
      _ => Future.successful(())
    )
}
