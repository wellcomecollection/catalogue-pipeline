package uk.ac.wellcome.mets.services

import scala.concurrent.ExecutionContext
import scala.util.Success
import akka.{Done, NotUsed}
import akka.stream.scaladsl._
import com.amazonaws.services.sqs.model.{Message => SQSMessage}

import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.messaging.sns.SNSMessageSender
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.mets.models._

import scala.concurrent.Future

case class SNSConfig(topicArn: String)

case class StorageUpdate(space: String, bagId: String)

case class MetsLocation(location: String)

class MetsAdaptorWorkerService(
  sqsStream: SQSStream[StorageUpdate],
  snsMsgSender: SNSMessageSender,
  bagRetriever: BagRetriever,
  concurrentConnections: Int = 6)(implicit ec: ExecutionContext)
    extends Runnable {

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    sqsStream.runStream(className, source => {
      source
        .via(retrieveBag)
        .via(getMetsLocation)
        .via(publishMetsLocation)
    })

  def retrieveBag: Flow[(SQSMessage, StorageUpdate), (SQSMessage, Bag), _] =
    Flow[(SQSMessage, StorageUpdate)]
      .mapAsync(concurrentConnections) {
        case (msg, update) => bagRetriever.getBag(update).map(bag => (msg, bag))
      }
      .collect { case (msg, Some(bag)) => (msg, bag) }

  def getMetsLocation: Flow[(SQSMessage, Bag), (SQSMessage, MetsLocation), _] =
    throw new NotImplementedError

  def publishMetsLocation
    : Flow[(SQSMessage, MetsLocation), SQSMessage, NotUsed] =
    Flow[(SQSMessage, MetsLocation)]
      .map {
        case (msg, metsLocation) =>
          (msg, snsMsgSender.sendT(metsLocation))
      }
      .collect { case (msg, Success(_)) => msg }
}
