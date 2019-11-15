package uk.ac.wellcome.mets_adapter.services

import scala.util.Success
import scala.concurrent.ExecutionContext
import akka.Done
import akka.stream.scaladsl._
import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import grizzled.slf4j.Logging

import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.messaging.sns.SNSMessageSender
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.mets_adapter.models._

import scala.concurrent.Future

case class Context[T](data: T, msg: SQSMessage)

class MetsAdapterWorkerService(
  msgStream: SQSStream[IngestUpdate],
  msgSender: SNSMessageSender,
  bagRetriever: BagRetriever,
  concurrentConnections: Int = 6)(implicit ec: ExecutionContext)
    extends Runnable with Logging {

  type Result[T] = Either[Throwable, T]

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.runStream(className, source => {
      source
        .via(retrieveBag)
        .via(parseMetsData)
        .via(publishMetsData)
    })

  def retrieveBag =
    Flow[(SQSMessage, IngestUpdate)]
      .mapAsync(concurrentConnections) {
        case (msg, update) =>
          bagRetriever
            .getBag(update)
            .transform(result => Success((msg, result.toEither)))
      }
      .via(logErrors)
      .collect { case (msg, Some(bag)) => (msg, bag) }

  def parseMetsData =
    Flow[(SQSMessage, Bag)]
      .map { case (msg, bag) => (msg, bag.metsData) }
      .via(logErrors)

  def publishMetsData =
    Flow[(SQSMessage, MetsData)]
      .map { case (msg, data) =>
        (msg, msgSender.sendT(data).toEither)
      }
      .via(logErrors)
      .map { case (msg, _) => msg }

  def logErrors[T] =
    Flow[(SQSMessage, Result[T])]
      .map { case (msg, result) =>
        result.left.map { err =>
          error(s"Error encountered processing SQS message. [Error]: ${err.getMessage} [Message]: ${msg}", err)
        }
        (msg, result)
      }
      .collect {
        case (msg, Right(data)) => (msg, data)
      }
}
