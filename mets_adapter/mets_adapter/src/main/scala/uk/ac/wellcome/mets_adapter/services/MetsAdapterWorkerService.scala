package uk.ac.wellcome.mets_adapter.services

import scala.concurrent.ExecutionContext
import scala.util.Success
import akka.{Done, NotUsed}
import akka.stream.scaladsl._
import com.amazonaws.services.sqs.model.{Message => SQSMessage}

import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.messaging.sns.SNSMessageSender
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.mets_adapter.models._

import scala.concurrent.Future

class MetsAdapterWorkerService(
  sqsStream: SQSStream[IngestUpdate],
  snsMsgSender: SNSMessageSender,
  bagRetriever: BagRetriever,
  concurrentConnections: Int = 6)(implicit ec: ExecutionContext)
    extends Runnable {

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    sqsStream.runStream(className, source => {
      source
        .via(getMetsLocation)
        .via(publishMetsLocation)
    })

  def getMetsLocation
    : Flow[(SQSMessage, IngestUpdate), (SQSMessage, MetsLocation), NotUsed] =
    Flow[(SQSMessage, IngestUpdate)]
      .mapAsync(concurrentConnections) {
        case (msg, update) => bagRetriever.getBag(update).map(bag => (msg, bag))
      }
      .collect { case (msg, Some(bag)) => (msg, bag.metsLocation) }
      .collect { case (msg, Some(metsLocation)) => (msg, metsLocation) }

  def publishMetsLocation
    : Flow[(SQSMessage, MetsLocation), SQSMessage, NotUsed] =
    Flow[(SQSMessage, MetsLocation)]
      .map {
        case (msg, metsLocation) =>
          (msg, snsMsgSender.sendT(metsLocation))
      }
      .collect { case (msg, Success(_)) => msg }
}
