package uk.ac.wellcome.platform.api

import scala.concurrent.ExecutionContext
import akka.http.scaladsl.model.{HttpEntity, MediaTypes}
import akka.http.scaladsl.server.{
  Directives,
  MalformedQueryParamRejection,
  RejectionHandler,
  Route,
  ValidationRejection
}
import com.sksamuel.elastic4s.ElasticClient
import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.platform.api.swagger.SwaggerDocs
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.platform.api.rest.{CustomDirectives, WorksController}

class Router(elasticClient: ElasticClient,
             elasticConfig: ElasticConfig,
             implicit val apiConfig: ApiConfig)(implicit ec: ExecutionContext)
    extends Directives
    with CustomDirectives {

  def routes: Route = handleRejections(rejectionHandler) {
    ignoreTrailingSlash {
      pathPrefix(apiConfig.pathPrefix) {
        concat(
          pathPrefix("v2") {
            concat(
              path("works") {
                MultipleWorksParams.parse { worksController.multipleWorks }
              },
              path("works" / Segment) { workId: String =>
                SingleWorkParams.parse { worksController.singleWork(workId, _) }
              },
              path("context.json") {
                getFromResource("context-v2.json")
              },
              path("swagger.json") {
                swagger
              }
            )
          },
          path("v1" / Remaining) { _ =>
            gone(
              """"
                |This API is now decommissioned.
                |Please use https://api.wellcomecollection.org/catalogue/v2/works.
              """.stripMargin.replace('\n', ' ')
            )
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

  lazy val worksController =
    new WorksController(elasticClient, apiConfig, elasticConfig)

  def swagger: Route = get {
    complete(
      HttpEntity(MediaTypes.`application/json`, swaggerDocs.json)
    )
  }

  val swaggerDocs = new SwaggerDocs(apiConfig)

  def getClusterHealth: Route = {
    import com.sksamuel.elastic4s.ElasticDsl._
    getWithFuture {
      elasticClient.execute(clusterHealth()).map { health =>
        complete(health.status)
      }
    }
  }

  def rejectionHandler =
    RejectionHandler.newBuilder
      .handle {
        case MalformedQueryParamRejection(field, msg, _) =>
          invalidRequest(s"$field: $msg")
        case ValidationRejection(msg, _) =>
          invalidRequest(s"$msg")
      }
      .handleNotFound(extractPublicUri { uri =>
        notFound(s"Page not found for URL ${uri.path}")
      })
      .result
}
