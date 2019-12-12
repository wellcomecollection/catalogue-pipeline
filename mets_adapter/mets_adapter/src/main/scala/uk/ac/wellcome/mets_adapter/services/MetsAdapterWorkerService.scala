package uk.ac.wellcome.mets_adapter.services

import scala.util.Success
import scala.concurrent.ExecutionContext
import akka.{Done, NotUsed}
import akka.stream.scaladsl._
import com.amazonaws.services.sqs.model.{Message => SQSMessage}
import grizzled.slf4j.Logging
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSMessageSender}
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.mets_adapter.models._
import uk.ac.wellcome.storage.Version

import scala.concurrent.Future

/** Processes SQS messages representing bag ingests on storage-service, and
  *  publishes METS data for use in the pipeline.
  *
  *  Consists of the following stages:
  *  - Retrieve the bag from storarge-service with the given ID
  *  - Parse METS data (which contains paths to the XML files) from the bag
  *  - Store the METS data in the VHS
  *  - Publish the VHS key to SNS
  */
class MetsAdapterWorkerService(
  msgStream: SQSStream[NotificationMessage],
  msgSender: SNSMessageSender,
  bagRetriever: BagRetriever,
  metsStore: MetsStore,
  concurrentHttpConnections: Int = 6,
  concurrentDynamoConnections: Int = 4)(implicit ec: ExecutionContext)
    extends Runnable
    with Logging {

  /** Encapsulates context to pass along each akka-stream stage. Newer versions
    *  of akka-streams have the asSourceWithContext/ asFlowWithContext idioms for
    *  this purpose, which we can migrate to if the library is updated.
    */
  case class Context(msg: SQSMessage, bagId: String)

  type Result[T] = Either[Throwable, T]

  case class MetsDataAndXml(data: MetsData, xml: String)

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.runStream(
      className,
      source => {
        source
          .via(unwrapMessage)
          .via(retrieveBag)
          .via(parseMetsData)
          .via(storeMetsData)
          .via(publishKey)
          .map { case (Context(msg, _), _) => msg }
      }
    )

  def unwrapMessage =
    Flow[(SQSMessage, NotificationMessage)]
      .map {
        case (msg, NotificationMessage(body)) =>
          (msg, fromJson[IngestUpdate](body).toEither)
      }
      .via(catchErrors)
      .map {
        case (msg, update) =>
          (Context(msg, update.context.externalIdentifier), update)
      }

  def retrieveBag =
    Flow[(Context, IngestUpdate)]
      .mapAsync(concurrentHttpConnections) {
        case (ctx, update) =>
          bagRetriever
            .getBag(update)
            .transform(result => Success((ctx, result.toEither)))
      }
      .via(catchErrors)

  def parseMetsData =
    Flow[(Context, Bag)]
      .mapWithContext { case (ctx, bag) => bag.metsData }

  def storeMetsData =
    Flow[(Context, MetsData)]
      .mapWithContextAsync(concurrentDynamoConnections) {
        case (ctx, data) =>
          Future {
            metsStore.storeData(Version(ctx.bagId, data.version), data)
          }
      }

  def publishKey =
    Flow[(Context, Version[String, Int])]
      .mapWithContext {
        case (ctx, data) => msgSender.sendT(data).toEither.right.map(_ => data)
      }

  /** Allows mapping a flow with a function, where:
    *  - Context is passed through.
    *  - Any errors are caught and the message prevented from propagating downstream,
    *    resulting in the message being put back on the queue / on the dlq.
    */
  implicit class ContextFlowOps[In, Out](
    val flow: Flow[(Context, In), (Context, Out), NotUsed]) {

    def mapWithContext[T](f: (Context, Out) => Result[T]) =
      flow
        .map { case (ctx, data) => (ctx, f(ctx, data)) }
        .via(catchErrors)

    def mapWithContextAsync[T](parallelism: Int)(
      f: (Context, Out) => Future[Result[T]]) =
      flow
        .mapAsync(parallelism) {
          case (ctx, data) => f(ctx, data).map((ctx, _))
        }
        .via(catchErrors)
  }

  def catchErrors[C, T] =
    Flow[(C, Result[T])]
      .map {
        case (ctx, result) =>
          result.left.map { err =>
            error(
              s"Error encountered processing SQS message. [Error]: ${err.getMessage} [Context]: ${ctx}",
              err)
          }
          (ctx, result)
      }
      .collect {
        case (ctx, Right(data)) => (ctx, data)
      }
}
