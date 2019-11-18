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

/** Encapsulates context to pass along each akka-stream stage. Newer versions
 *  of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
 *  this purpose, which we can migrate to if the library is updated.
 */
case class Context(msg: SQSMessage, bagId: String)

class MetsAdapterWorkerService(
  msgStream: SQSStream[IngestUpdate],
  msgSender: SNSMessageSender,
  bagRetriever: BagRetriever,
  metsStore: MetsStore,
  concurrentConnections: Int = 6)(implicit ec: ExecutionContext)
    extends Runnable with Logging {

  type Result[T] = Either[Throwable, T]

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.runStream(className, source => {
      source
        .map { case (msg, update) => (Context(msg, update.bagId), update) }
        .via(retrieveBag)
        .via(parseMetsData)
        .via(storeMetsData)
        .via(publishMetsData)
    })

  def retrieveBag =
    Flow[(Context, IngestUpdate)]
      .mapAsync(concurrentConnections) { case (ctx, update) =>
        bagRetriever
          .getBag(update)
          .transform(result => Success((ctx, result.toEither)))
      }
      .via(logErrors)
      .collect { case (msg, Some(bag)) => (msg, bag) }

  def parseMetsData =
    Flow[(Context, Bag)]
      .map { case (ctx, bag) => (ctx, bag.metsData) }
      .via(logErrors)

  def storeMetsData =
    Flow[(Context, MetsData)]
      .map { case (ctx, data) => (ctx, metsStore.storeMetsData(ctx.bagId, data)) }
      .via(logErrors)
      .collect { case (ctx, Some(data)) => (ctx, data) }

  def publishMetsData =
    Flow[(Context, MetsData)]
      .map { case (ctx, data) =>
        (ctx, msgSender.sendT(data).toEither)
      }
      .via(logErrors)
      .map { case (Context(msg, _), _) => msg }

  def logErrors[T] =
    Flow[(Context, Result[T])]
      .map { case (ctx, result) =>
        result.left.map { err =>
          error(s"Error encountered processing SQS message. [Error]: ${err.getMessage} [Message]: ${ctx.msg}", err)
        }
        (ctx, result)
      }
      .collect {
        case (ctx, Right(data)) => (ctx, data)
      }
}
