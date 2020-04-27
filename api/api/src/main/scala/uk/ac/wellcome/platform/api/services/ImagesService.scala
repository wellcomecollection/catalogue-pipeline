package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.{ElasticClient, ElasticError, Index}
import uk.ac.wellcome.models.work.internal.AugmentedImage

import scala.concurrent.Future

class ImagesService(elasticClient: ElasticClient) {
  def findImageById(id: String)(
    index: Index): Future[Either[ElasticError, Option[AugmentedImage]]] = ???
}
