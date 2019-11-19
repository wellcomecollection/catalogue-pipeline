package uk.ac.wellcome.mets_adapter.services

import scala.util.Success
import scala.concurrent.ExecutionContext
import akka.{Done, NotUsed}
import akka.stream.scaladsl._
import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import grizzled.slf4j.Logging

import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.messaging.sns.SNSMessageSender
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.mets_adapter.models._

import scala.concurrent.Future

/** Processes SQS messages representing bag ingests on storage-service, and
 *  publishes METS data for use in the pipeline.
 *
 *  Consists of the following stages:
 *  - Retrieve the bag from storarge-service with the given ID
 *  - Parse METS data from the bag
 *  - Check whether the METS data is new / changed, and ignore it otherwise
 *  - Publish the METS data to SNS
 *  - Store the METS data so in future can tell if it's seen before
 */
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
        .via(filterMetsData)
        .via(publishMetsData)
        .via(storeMetsData)
        .map { case (Context(msg, _), _) => msg }
    })

  def retrieveBag =
    Flow[(Context, IngestUpdate)]
      .mapAsync(concurrentConnections) { case (ctx, update) =>
        bagRetriever
          .getBag(update)
          .transform(result => Success((ctx, result.toEither)))
      }
      .via(catchErrors)

  def parseMetsData =
    Flow[(Context, Option[Bag])]
      .mapWithContext { case (ctx, bag) => bag.metsData }

  def filterMetsData =
    Flow[(Context, Option[MetsData])]
      .mapOptionalWithContext {
        case (ctx, data) => metsStore.filterMetsData(ctx.bagId, data)
      }

  def publishMetsData =
    Flow[(Context, Option[MetsData])]
      .mapWithContext {
        case (ctx, data) => msgSender.sendT(data).toEither.right.map(_ => data)
      }

  def storeMetsData =
    Flow[(Context, Option[MetsData])]
      .mapWithContext {
        case (ctx, data) => metsStore.storeMetsData(ctx.bagId, data)
      }

  /** Encapsulates context to pass along each akka-stream stage. Newer versions
   *  of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
   *  this purpose, which we can migrate to if the library is updated.
   */
  case class Context(msg: SQSMessage, bagId: String)

  /** Allows mapping a flow with a function, where:
   *  - Context is passed through.
   *  - None values are ignored and passed to the next stage: this is required
   *    for the SQS delete action to trigger at the end of the stream
   *  - Any errors are caught and the message prevented from propagating downstream,
   *    resulting in the message being put back on the queue / on the dlq.
   */
  implicit class ContextFlowOps[In, Out](
    val flow: Flow[(Context, In), (Context, Option[Out]), NotUsed]) {

    def mapWithContext[T](f: (Context, Out) => Result[T]) =
      mapOptionalWithContext { case (ctx, data) => f(ctx, data).map(Some(_)) }

    def mapOptionalWithContext[T](f: (Context, Out) => Result[Option[T]]) =
      flow
        .map {
          case (ctx, Some(data)) => (ctx, f(ctx, data))
          case (ctx, None) => (ctx, Right(None))
        }
        .via(catchErrors)
  }

  def catchErrors[T] =
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
