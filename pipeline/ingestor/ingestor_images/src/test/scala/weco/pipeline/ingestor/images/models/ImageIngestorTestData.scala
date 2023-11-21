package weco.pipeline.ingestor.images.models

import weco.catalogue.internal_model.image._
import weco.json.JsonUtil._

import scala.io.Source
trait ImageIngestorTestData {
  lazy val testImage: Image[ImageState.Augmented] =
    fromJson[Image[ImageState.Augmented]](
      Source.fromResource("zswkgyan-augmented.json").mkString
    ).get
}
