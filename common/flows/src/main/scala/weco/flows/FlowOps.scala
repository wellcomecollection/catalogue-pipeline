package weco.flows

import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}
import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge}

trait FlowOps extends Logging {

  implicit val ec: ExecutionContext

  type Result[T] = Either[Throwable, T]

  /** Allows mapping a flow with a function, where:
    *   - Context is passed through.
    *   - Any errors are caught and the message prevented from propagating
    *     downstream, resulting in the message being put back on the queue / on
    *     the dlq.
    *   - None values are ignored but passed through (so they don't end up on
    *     the dlq)
    */
  implicit class ContextFlowOps[Ctx, In, Out](
    val flow: Flow[(Ctx, In), (Ctx, Option[Out]), NotUsed]
  ) {

    def mapWithContext[T](f: (Ctx, Out) => Result[T]) =
      flow
        .map {
          case (ctx, Some(data)) => (ctx, f(ctx, data).map(Some(_)))
          case (ctx, None)       => (ctx, Right(None))
        }
        .via(catchErrors)

    def mapWithContextAsync[T](
      parallelism: Int
    )(f: (Ctx, Out) => Future[Result[T]]) =
      flow
        .mapAsync(parallelism) {
          case (ctx, Some(data)) =>
            f(ctx, data).map(out => (ctx, out.map(Some(_))))
          case (ctx, None) => Future.successful((ctx, Right(None)))
        }
        .via(catchErrors)
  }

  def catchErrors[C, T] =
    Flow[(C, Result[T])]
      .map { case (ctx, result) =>
        result.left.map { err =>
          error(
            s"Error encountered processing SQS message. [Error]: ${err.getMessage} [Context]: $ctx",
            err
          )
        }
        (ctx, result)
      }
      .collect { case (ctx, Right(data)) => (ctx, data) }

  /** Broadcasts the output of a flow to flows `a` and `b` and merges them again
    */
  def broadcastAndMerge[I, O](
    a: Flow[I, O, NotUsed],
    b: Flow[I, O, NotUsed]
  ): Flow[I, O, NotUsed] =
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[I](2))
        val merge = builder.add(Merge[O](2))
        broadcast ~> a ~> merge
        broadcast ~> b ~> merge
        FlowShape(broadcast.in, merge.out)
      }
    )
}
