package weco.catalogue.internal_model.image

import weco.catalogue.internal_model.DerivedDataCommon
import weco.catalogue.internal_model.locations.{DigitalLocation, LocationType}

case class DerivedImageData(
  thumbnail: DigitalLocation,
  sourceContributorAgents: List[String] = Nil,
)

object DerivedImageData extends DerivedDataCommon {
  def apply(image: Image[_]): DerivedImageData =
    DerivedImageData(
      thumbnail = thumbnailLocation(image),
      sourceContributorAgents = sourceContributorAgents(image.source)
    )

  private def thumbnailLocation(image: Image[_]): DigitalLocation =
    image.locations
      .find(_.locationType == LocationType.IIIFImageAPI)
      .getOrElse(
        // This should never happen
        throw new RuntimeException(
          s"No iiif-image (thumbnail) location found on image ${image.sourceIdentifier}")
      )

  private def sourceContributorAgents(source: ImageSource): List[String] =
    source match {
      case ParentWorks(canonicalWork, _) =>
        contributorAgentLabels(canonicalWork.data.map(_.contributors).getOrElse(List()))
      case _ => Nil
    }
}
