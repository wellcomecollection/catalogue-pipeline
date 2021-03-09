package uk.ac.wellcome.models.work.internal

case class DerivedWorkData(
  contributorAgents: List[String] = Nil,
)

case class DerivedImageData(
  thumbnail: DigitalLocation,
  sourceContributorAgents: List[String] = Nil,
)

trait DerivedDataCommon {
  protected def contributorAgentLabels(
    contributors: List[Contributor[_]]): List[String] =
    contributors.map(_.agent.typedLabel)
}

object DerivedWorkData extends DerivedDataCommon {
  def apply(data: WorkData[_]): DerivedWorkData =
    DerivedWorkData(
      contributorAgents = contributorAgentLabels(data.contributors)
    )

  def none: DerivedWorkData =
    DerivedWorkData(
      contributorAgents = Nil
    )
}

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
      case SourceWorks(canonicalWork, redirectedWork) =>
        contributorAgentLabels(
          canonicalWork.data.contributors ++
            redirectedWork.map(_.data.contributors).getOrElse(Nil)
        ).distinct
      case _ => Nil
    }
}
