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
import uk.ac.wellcome.storage.{Version, ObjectLocation}
import uk.ac.wellcome.storage.store.TypedStore

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
  xmlStore: TypedStore[ObjectLocation, String],
  metsStore: MetsStore,
  concurrentHttpConnections: Int = 6,
  concurrentS3Connections: Int = 4)(implicit ec: ExecutionContext)
    extends Runnable
    with Logging {

  type Result[T] = Either[Throwable, T]

  case class MetsDataAndXml(data: MetsData, xml: String)

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.runStream(
      className,
      source => {
        source
          .map { case (msg, update) => (Context(msg, update.bagId), update) }
          .via(retrieveBag)
          .via(parseMetsData)
          .via(retrieveXml)
          .via(storeXml)
          .via(publishKey)
          .map { case (Context(msg, _), _) => msg }
      }
    )

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
    Flow[(Context, Option[Bag])]
      .mapWithContext { case (ctx, bag) => bag.metsData }

  def retrieveXml =
    Flow[(Context, Option[MetsData])]
      .mapWithContextAsync(concurrentS3Connections) { case (ctx, data) =>
        Future {
          xmlStore
            .get(ObjectLocation(data.bucket, data.path))
            .right
            .map(obj => MetsDataAndXml(data, obj.identifiedT.t))
            .left
            .map(_.e)
        }
      }

  def storeXml =
    Flow[(Context, Option[MetsDataAndXml])]
      .mapWithContext { case (ctx, MetsDataAndXml(data, xml)) =>
        metsStore.storeXml(Version(ctx.bagId, data.version), xml)
      }

  def publishKey =
    Flow[(Context, Option[Version[String, Int]])]
      .mapWithContext {
        case (ctx, data) => msgSender.sendT(data).toEither.right.map(_ => data)
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
      mapWithContextAsync(1) {
        case (ctx, data) => Future.successful(f(ctx, data))
      }

    def mapOptionalWithContext[T](f: (Context, Out) => Result[Option[T]]) =
      mapOptionalWithContextAsync(1) {
        case (ctx, data) => Future.successful(f(ctx, data))
      }

    def mapWithContextAsync[T](parallelism: Int)(
      f: (Context, Out) => Future[Result[T]]) =
      mapOptionalWithContextAsync(parallelism) {
        case (ctx, data) => f(ctx, data).map(res => res.map(Some(_)))
      }

    def mapOptionalWithContextAsync[T](parallelism: Int)(
      f: (Context, Out) => Future[Result[Option[T]]]) =
      flow
        .mapAsync(parallelism) {
          case (ctx, Some(data)) => f(ctx, data).map((ctx, _))
          case (ctx, None)       => Future.successful((ctx, Right(None)))
        }
        .via(catchErrors)
  }

  def catchErrors[T] =
    Flow[(Context, Result[T])]
      .map {
        case (ctx, result) =>
          result.left.map { err =>
            error(
              s"Error encountered processing SQS message. [Error]: ${err.getMessage} [Message]: ${ctx.msg}",
              err)
          }
          (ctx, result)
      }
      .collect {
        case (ctx, Right(data)) => (ctx, data)
      }
}
