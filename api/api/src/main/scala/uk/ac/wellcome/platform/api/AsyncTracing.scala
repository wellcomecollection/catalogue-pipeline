package uk.ac.wellcome.platform.api

import co.elastic.apm.api.ElasticApm

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait AsyncTracing {
  def spanFuture[T](name: String,
                    spanType: String = "",
                    subType: String = "",
                    action: String = "")(wrappedFunction: => Future[T])(
    implicit ec: ExecutionContext): Future[T] = {
    val span =
      ElasticApm
        .currentTransaction()
        .startSpan(spanType, subType, action)
        .setName(name)
    try {
      wrappedFunction.map { f =>
        span.end()
        f
      }
    } catch {
      case NonFatal(e) =>
        span.captureException(e)
        span.end()
        throw e
    }
  }
}
