package weco.catalogue.tei.id_extractor

import uk.ac.wellcome.messaging.sns.SNSMessageSender
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

case class TeiIdExtractorWorkerService(messageStream: SQSStream[Nothing],
                                       messageSender: SNSMessageSender)
    extends Runnable {
  override def run(): Future[Any] = ???
}
