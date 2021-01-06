package uk.ac.wellcome.platform.api

import scala.concurrent.ExecutionContext
import akka.http.scaladsl.model.{HttpEntity, MediaTypes}
import akka.http.scaladsl.server.{
  MalformedQueryParamRejection,
  RejectionHandler,
  Route,
  ValidationRejection
}
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.platform.api.elasticsearch.{
  ImagesMultiMatcher,
  WorksMultiMatcher
}
import uk.ac.wellcome.platform.api.swagger.SwaggerDocs
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.platform.api.rest._
import uk.ac.wellcome.platform.api.services.ElasticsearchService

class Router(elasticClient: ElasticClient,
             elasticConfig: ElasticConfig,
             queryConfig: QueryConfig,
             swaggerDocs: SwaggerDocs,
             implicit val apiConfig: ApiConfig)(implicit ec: ExecutionContext)
    extends CustomDirectives {

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
              path("images") {
                MultipleImagesParams.parse { imagesController.multipleImages }
              },
              path("images" / Segment) { imageId: String =>
                SingleImageParams.parse {
                  imagesController.singleImage(imageId, _)
                }
              },
              path("context.json") {
                getFromResource("context-v2.json")
              },
              path("swagger.json") {
                swagger
              },
<<<<<<< HEAD
              path("search-templates.json") {
                getSearchTemplates
              }
=======
              path("work-search-templates.json") {
                getWorkSearchTemplates
              }
                path ("image-search-templates.json") {
                  getImageSearchTemplates
                }
>>>>>>> 7a14093341d5b70b011f6c5cea07caa0c0e49e2b
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

  lazy val elasticsearchService = new ElasticsearchService(elasticClient)

  lazy val worksController =
    new WorksController(
      elasticsearchService,
      apiConfig,
      worksIndex = elasticConfig.worksIndex)

  lazy val imagesController =
    new ImagesController(
      elasticsearchService,
      apiConfig,
      imagesIndex = elasticConfig.imagesIndex,
      queryConfig)

  def swagger: Route = get {
    complete(
      HttpEntity(MediaTypes.`application/json`, swaggerDocs.json)
    )
  }

  def getClusterHealth: Route = {
    import com.sksamuel.elastic4s.ElasticDsl._
    getWithFuture {
      elasticClient.execute(clusterHealth()).map { health =>
        complete(health.status)
      }
    }
  }

  def getSearchTemplates: Route = get {
    val worksSearchTemplate = SearchTemplate(
      "multi_matcher_search_query",
      elasticConfig.worksIndex.name,
      WorksMultiMatcher("{{query}}").filter(
        termQuery(field = "type", value = "Visible")),
      )

    val imageSearchTemplate = SearchTemplate(
      "image_search_query",
      elasticConfig.imagesIndex.name,
      ImagesMultiMatcher("{{query}}"),
    )

    complete(SearchTemplateResponse(
      List(worksSearchTemplate), List(imageSearchTemplate)
    ))
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
