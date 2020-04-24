package uk.ac.wellcome.platform.api.rest

import akka.http.scaladsl.model.StatusCodes.{
  BadRequest,
  Gone,
  InternalServerError,
  NotFound
}
import akka.http.scaladsl.model.Uri
import uk.ac.wellcome.platform.api.models.{
  ApiConfig,
  DisplayError,
  ErrorVariant
}
import akka.http.scaladsl.server.{Directive, Directives, Route}
import com.sksamuel.elastic4s.ElasticError
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Printer
import uk.ac.wellcome.display.json.DisplayJsonUtil
import uk.ac.wellcome.display.models.ApiVersions
import uk.ac.wellcome.platform.api.ResultResponse
import uk.ac.wellcome.platform.api.elasticsearch.ElasticsearchErrorHandler

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait CustomDirectives extends Directives with FailFastCirceSupport {
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
    error(
      ElasticsearchErrorHandler.buildDisplayError(err)
    )

  def gone(message: String): Route = error(
    DisplayError(
      ErrorVariant.http410,
      Some(message)
    )
  )

  def notFound(message: String): Route = error(
    DisplayError(
      ErrorVariant.http404,
      Some(message)
    )
  )

  def invalidRequest(message: String): Route = error(
    DisplayError(
      ErrorVariant.http400,
      Some(message)
    )
  )

  def getWithFuture(future: Future[Route]): Route =
    get {
      onComplete(future) {
        case Success(resp) => resp
        case Failure(_) =>
          error(
            DisplayError(ErrorVariant.http500, Some("Unhandled error."))
          )
      }
    }

  private def error(err: DisplayError): Route = {
    val status = err.httpStatus match {
      case Some(400) => BadRequest
      case Some(404) => NotFound
      case Some(410) => Gone
      case Some(500) => InternalServerError
      case _         => InternalServerError
    }
    complete(status -> ResultResponse(context = contextUri, result = err))
  }

  implicit val jsonPrinter: Printer = DisplayJsonUtil.printer
}
