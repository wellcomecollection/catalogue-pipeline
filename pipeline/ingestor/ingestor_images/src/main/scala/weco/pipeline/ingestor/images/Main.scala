package weco.pipeline.ingestor.images

import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.{Augmented, Indexed}
import weco.catalogue.internal_model.index.ImagesIndexConfig
import weco.elasticsearch.IndexConfig
import weco.pipeline.ingestor.common.IngestorMain

object Main extends IngestorMain[Image[Augmented], Image[Indexed]] {
  override val name: String = "images"

  override val inputIndexField: String = "es.augmented-images.index"
  override val outputIndexField: String = "es.indexed-images.index"

  override val indexConfig: IndexConfig = ImagesIndexConfig.indexed

  override val transform: Image[Augmented] => Image[Indexed] =
    ImageTransformer.deriveData

  runWithConfig { config =>
    runIngestor(config)
  }
}
