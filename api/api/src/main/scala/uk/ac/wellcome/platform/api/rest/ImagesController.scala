package uk.ac.wellcome.platform.api.rest

import akka.http.scaladsl.server.Route
import com.sksamuel.elastic4s.{ElasticClient, Index}
import uk.ac.wellcome.display.models.v2._
import uk.ac.wellcome.display.models.Implicits._
import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.platform.api.Tracing
import uk.ac.wellcome.platform.api.models.ApiConfig
import uk.ac.wellcome.platform.api.services.ImagesService

import scala.concurrent.{ExecutionContext, Future}

class ImagesController(
  elasticClient: ElasticClient,
  implicit val apiConfig: ApiConfig,
  elasticConfig: ElasticConfig)(implicit ec: ExecutionContext)
    extends CustomDirectives
    with Tracing {

  def singleImage(id: String, params: SingleImageParams): Route =
    getWithFuture {
      transactFuture("GET /images/{imageId}") {
        val index = params._index.map(Index(_)).getOrElse(elasticConfig.index)
        imagesService
          .findImageById(id)(index)
          .flatMap {
            case Right(Some(image)) =>
              Future.successful(
                complete(
                  ResultResponse(
                    context = contextUri,
                    result = DisplayImage(image)
                  )
                )
              )
            case Right(None) =>
              Future.successful(notFound(s"Image not found for identifier $id"))
            case Left(err) => Future.successful(elasticError(err))
          }
      }
    }

  private lazy val imagesService = new ImagesService(elasticClient)
}
