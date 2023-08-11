package weco.pipeline.merger.models

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.locations.License

case class MergerImageData[State](imageData: ImageData[State]) extends Logging {
  def copyLicenceFrom(
    sources: Seq[ImageData[State]]
  ): ImageData[State] = {
    getDistinctLicencesFrom(sources) match {
      // Only copy the licence if there is no ambiguity.
      // The purpose of this is to harmonise the licences on a work when the source overrides the target
      // if there are multiple conflicting sources, then we might as well leave it as it is.
      case Seq(licence) => insertLicence(licence)
      case Nil =>
        info("no source licences present, leaving original licence")
        imageData
      case seq: Seq[License] =>
        warn(
          s"multiple source licences present: ${seq.mkString(", ")}, cannot choose, leaving original licence"
        )
        imageData
    }
  }

  private def getDistinctLicencesFrom(
    sources: Seq[ImageData[State]]
  ): Seq[License] = sources
    .flatMap(_.locations)
    .flatMap(_.license)
    .distinct

  private def insertLicence[T](
    newLicence: License
  ): ImageData[State] =
    imageData.copy(locations =
      imageData.locations.map(_.copy(license = Some(newLicence)))
    )
}
