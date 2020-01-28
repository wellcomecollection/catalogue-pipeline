package uk.ac.wellcome.bigmessaging

import grizzled.slf4j.Logging
import scala.concurrent.{Future, ExecutionContext}
import akka.NotUsed
import akka.stream.scaladsl._

trait FlowOps extends Logging {

  implicit val ec: ExecutionContext

  type Result[T] = Either[Throwable, T]

  /** Allows mapping a flow with a function, where:
    *  - Context is passed through.
    *  - Any errors are caught and the message prevented from propagating downstream,
    *    resulting in the message being put back on the queue / on the dlq.
    *  - None values are ignored but passed through (so they don't end up on the dlq)
    */
  implicit class ContextFlowOps[Ctx, In, Out](
    val flow: Flow[(Ctx, In), (Ctx, Option[Out]), NotUsed]) {

    def mapWithContext[T](f: (Ctx, Out) => Result[T]) =
      flow
        .map {
          case (ctx, Some(data)) => (ctx, (f(ctx, data).map(Some(_))))
          case (ctx, None)       => (ctx, Right(None))
        }
        .via(catchErrors)

    def mapWithContextAsync[T](parallelism: Int)(
      f: (Ctx, Out) => Future[Result[T]]) =
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
      .map {
        case (ctx, result) =>
          result.left.map { err =>
            error(
              s"Error encountered processing SQS message. [Error]: ${err.getMessage} [Context]: ${ctx}",
              err)
          }
          (ctx, result)
      }
      .collect { case (ctx, Right(data)) => (ctx, data)
}
}
