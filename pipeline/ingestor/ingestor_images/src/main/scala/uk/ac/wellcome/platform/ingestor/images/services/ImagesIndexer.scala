package uk.ac.wellcome.platform.ingestor.images.services

import com.sksamuel.elastic4s.{ElasticClient, Index, Indexable}
import uk.ac.wellcome.elasticsearch.model.{CanonicalId, Version}
import uk.ac.wellcome.json.JsonUtil
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.AugmentedImage
import uk.ac.wellcome.platform.ingestor.common.Indexer

import scala.concurrent.ExecutionContext

class ImagesIndexer(val client: ElasticClient, val index: Index)(
  implicit val ec: ExecutionContext)
    extends Indexer[AugmentedImage] {
  override implicit val indexable: Indexable[AugmentedImage] = image =>
    JsonUtil.toJson(image).get
  override implicit val id: CanonicalId[AugmentedImage] = image =>
    image.id.canonicalId
  override implicit val version: Version[AugmentedImage] = image =>
    image.version
}
