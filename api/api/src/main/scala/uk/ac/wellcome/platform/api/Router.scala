package uk.ac.wellcome.platform.api

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.http.scaladsl.model.{HttpEntity, MediaTypes}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.{
  Directives,
  MalformedQueryParamRejection,
  RejectionHandler,
  Route,
  ValidationRejection
}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import com.sksamuel.elastic4s.{ElasticClient, ElasticError, Index}
import io.circe.Printer
import uk.ac.wellcome.platform.api.services.{ElasticsearchService, WorksService}
import uk.ac.wellcome.elasticsearch.DisplayElasticConfig
import uk.ac.wellcome.platform.api.elasticsearch.ElasticErrorHandler
import uk.ac.wellcome.platform.api.swagger.SwaggerDocs
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.display.models.v2._
import uk.ac.wellcome.display.models.Implicits._
import uk.ac.wellcome.display.json.DisplayJsonUtil

class Router(elasticClient: ElasticClient,
             elasticConfig: DisplayElasticConfig,
             apiConfig: ApiConfig)(implicit ec: ExecutionContext)
    extends FailFastCirceSupport
    with Directives
    with AsyncTracing {

  import MultipleWorksResponse.encoder
  import ResultResponse.encoder

  def routes: Route = handleRejections(rejectionHandler) {
    ignoreTrailingSlash {
      pathPrefix(apiConfig.pathPrefix) {
        concat(
          pathPrefix("v2") {
            concat(
              path("works") {
                MultipleWorksParams.parse { params =>
                  getWithFuture(multipleWorks(params))
                }
              },
              path("works" / Segment) { workId =>
                SingleWorkParams.parse { params =>
                  getWithFuture(singleWork(workId, params))
                }
              },
              path("context.json") {
                getFromFile(context.getPath)
              },
              path("swagger.json") {
                swagger
              }
            )
          },
          path("v1" / Remaining) { _ =>
            v1ApiGone
          },
          pathPrefix("management") {
            concat(
              path("healthcheck") {
                get {
                  complete("message" -> "ok")
                }
              },
              path("clusterhealth") {
                getClusterHealth
              }
            )
          }
        )
      }
    }
  }

  def multipleWorks(params: MultipleWorksParams): Future[Route] =
    transactFuture("GET /works")({
      val searchOptions = params.searchOptions(apiConfig)
      val index = params._index.map(Index(_)).getOrElse(elasticConfig.indexV2)
      worksService
        .listOrSearchWorks(index, searchOptions)
        .map {
          case Left(err) => elasticError(err)
          case Right(resultList) =>
            extractPublicUri { uri =>
              complete(
                MultipleWorksResponse(
                  resultList,
                  searchOptions,
                  params.include.getOrElse(V2WorksIncludes()),
                  uri,
                  contextUri
                )
              )
            }
        }
    })

  def singleWork(id: String, params: SingleWorkParams): Future[Route] =
    transactFuture("GET /works/{workId}")({
      val index = params._index.map(Index(_)).getOrElse(elasticConfig.indexV2)
      val includes = params.include.getOrElse(V2WorksIncludes())
      worksService
        .findWorkById(id)(index)
        .map {
          case Right(Some(work: IdentifiedWork))           => workFound(work, includes)
          case Right(Some(work: IdentifiedRedirectedWork)) => workRedirect(work)
          case Right(Some(_))                              => workGone
          case Right(None)                                 => workNotFound(id)
          case Left(err)                                   => elasticError(err)
        }
    })

  def workFound(work: IdentifiedWork, includes: V2WorksIncludes): Route =
    complete(
      ResultResponse(
        context = contextUri,
        result = DisplayWorkV2(work, includes)
      )
    )

  def workRedirect(work: IdentifiedRedirectedWork): Route =
    extractPublicUri { uri =>
      val newPath = (work.redirect.canonicalId :: uri.path.reverse.tail).reverse
      redirect(uri.withPath(newPath), Found)
    }

  def workGone: Route = error(
    DisplayError(
      ErrorVariant.http410,
      Some("This work has been deleted")
    )
  )

  def workNotFound(id: String): Route = error(
    DisplayError(
      ErrorVariant.http404,
      Some(s"Work not found for identifier ${id}")
    )
  )

  def v1ApiGone = error(
    DisplayError(
      ErrorVariant.http410,
      Some(
        """"
          |This API is now decommissioned.
          |Please use https://api.wellcomecollection.org/catalogue/v2/works.
        """.stripMargin.replace('\n', ' ')
      )
    )
  )

  def notFound = extractPublicUri { uri =>
    error(
      DisplayError(
        ErrorVariant.http404,
        Some(s"Page not found for URL ${uri.path}")
      )
    )
  }

  def invalidParam(msg: String) = error(
    DisplayError(ErrorVariant.http400, Some(s"$msg"))
  )

  def elasticError(err: ElasticError): Route = error(
    ElasticErrorHandler.buildDisplayError(err)
  )

  def error(err: DisplayError): Route = {
    val status = err.httpStatus match {
      case Some(400) => BadRequest
      case Some(404) => NotFound
      case Some(410) => Gone
      case Some(500) => InternalServerError
      case _         => InternalServerError
    }
    complete(status -> ResultResponse(context = contextUri, result = err))
  }

  def getClusterHealth: Route = {
    import com.sksamuel.elastic4s.ElasticDsl._
    getWithFuture {
      elasticClient.execute(clusterHealth()).map { health =>
        complete(health.status)
      }
    }
  }

  def swagger: Route = get {
    complete(
      HttpEntity(MediaTypes.`application/json`, swaggerDocs.json)
    )
  }

  def rejectionHandler =
    RejectionHandler.newBuilder
      .handle {
        case MalformedQueryParamRejection(field, msg, _) =>
          invalidParam(s"$field: $msg")
        case ValidationRejection(msg, _) =>
          invalidParam(s"$msg")
      }
      .handleNotFound(notFound)
      .result

  def getWithFuture(future: Future[Route]): Route =
    get {
      onComplete(future) {
        case Success(resp) => resp
        case Failure(err) =>
          error(
            DisplayError(ErrorVariant.http500, Some("Unhandled error."))
          )
      }
    }

  // Directive for getting public URI of the current request, using the host
  // and scheme as per the config.
  // (without this URIs end up looking like https://localhost:8888/..., rather
  // than https://api.wellcomecollection.org/...))
  def extractPublicUri =
    extractUri.map { uri =>
      uri
        .withHost(apiConfig.host)
        // akka-http uses 0 to indicate no explicit port in the URI
        .withPort(0)
        .withScheme(apiConfig.scheme)
    }

  val swaggerDocs = new SwaggerDocs(apiConfig)

  lazy val context = getClass.getResource("/context-v2.json")

  lazy val contextUri =
    apiConfig match {
      case ApiConfig(host, scheme, _, pathPrefix, contextSuffix) =>
        s"$scheme://$host/$pathPrefix/${ApiVersions.v2}/$contextSuffix"
    }

  lazy val worksService =
    new WorksService(new ElasticsearchService(elasticClient))

  implicit val jsonPrinter: Printer = DisplayJsonUtil.printer
}
