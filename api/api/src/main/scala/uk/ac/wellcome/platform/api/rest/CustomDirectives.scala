package uk.ac.wellcome.platform.api.rest

import akka.http.scaladsl.model.Uri
import uk.ac.wellcome.platform.api.models._
import akka.http.scaladsl.server.{Directive, Directives, Route}
import com.sksamuel.elastic4s.ElasticError
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import grizzled.slf4j.Logging
import io.circe.Printer
import uk.ac.wellcome.display.json.DisplayJsonUtil
import uk.ac.wellcome.display.models.ApiVersions
import uk.ac.wellcome.platform.api.elasticsearch.ElasticsearchErrorHandler

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait CustomDirectives extends Directives with FailFastCirceSupport with Logging {
  import ResultResponse.encoder

  implicit val apiConfig: ApiConfig

  // Directive for getting public URI of the current request, using the host
  // and scheme as per the config.
  // (without this URIs end up looking like https://localhost:8888/..., rather
  // than https://api.wellcomecollection.org/...))
  def extractPublicUri: Directive[Tuple1[Uri]] =
    extractUri.map { uri =>
      uri
        .withHost(apiConfig.host)
        // akka-http uses 0 to indicate no explicit port in the URI
        .withPort(0)
        .withScheme(apiConfig.scheme)
    }

  def contextUri: String =
    apiConfig match {
      case ApiConfig(host, scheme, _, pathPrefix, contextSuffix) =>
        s"$scheme://$host/$pathPrefix/${ApiVersions.v2}/$contextSuffix"
    }

  def elasticError(err: ElasticError): Route =
    errorRoute(
      ElasticsearchErrorHandler.buildDisplayError(err)
    )

  def gone(description: String): Route =
    errorRoute(
      DisplayError(variant = ErrorVariant.http410, description = description)
    )

  def notFound(description: String): Route =
    errorRoute(
      DisplayError(variant = ErrorVariant.http404, description = description)
    )

  def invalidRequest(description: String): Route =
    errorRoute(
      DisplayError(variant = ErrorVariant.http400, description = description)
    )

  def internalError(err: Throwable): Route = {
    error(s"Sending HTTP 500: $err", err)
    errorRoute(DisplayError(variant = ErrorVariant.http500))
  }

  def getWithFuture(future: Future[Route]): Route =
    get {
      onComplete(future) {
        case Success(resp) => resp
        case Failure(err)  => internalError(err)
      }
    }

  private def errorRoute(err: DisplayError): Route =
    complete(
      err.httpStatus -> ResultResponse(context = contextUri, result = err)
    )

  implicit val jsonPrinter: Printer = DisplayJsonUtil.printer
}
