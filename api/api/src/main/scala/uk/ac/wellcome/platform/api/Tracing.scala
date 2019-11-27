package uk.ac.wellcome.platform.api

import co.elastic.apm.api.{ElasticApm, Transaction}
import co.elastic.apm.attach.ElasticApmAttacher
import com.typesafe.config.Config
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

object Tracing {
  def init(config: Config): Unit = {
    ElasticApmAttacher.attach(
      Map(
        "application_packages" -> "uk.ac.wellcome",
        "service_name" -> config.getOrElse("apm.service.name")("catalogue-api"),
        "server_urls" -> config.getOrElse("apm.server.url")(
          "http://localhost:9200"),
        "secret_token" -> config.getOrElse[String]("apm.secret")("")
      ).asJava)
  }
}

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

  def transactFuture[T](name: String,
                        transactionType: String = Transaction.TYPE_REQUEST)(
    wrappedFunction: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val transaction = ElasticApm.startTransaction()
    val scope = transaction.activate()
    try {
      transaction
        .setName(name)
        .setType(transactionType)
      wrappedFunction.transform { res =>
        res match {
          case Success(_)      =>
          case Failure(reason) => transaction.captureException(reason)
        }
        transaction.end()
        scope.close()
        res
      }
    } catch {
      case NonFatal(e) =>
        transaction.captureException(e)
        transaction.end()
        scope.close()
        throw e
    }
  }
}
