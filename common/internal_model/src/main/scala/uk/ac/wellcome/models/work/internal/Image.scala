package uk.ac.wellcome.models.work.internal

sealed trait BaseImage[+Id <: WithSourceIdentifier] extends HasIdState[Id] {
  val id: Id
  val location: DigitalLocation
}

case class UnmergedImage[Id <: WithSourceIdentifier](
  id: Id,
  version: Int,
  location: DigitalLocation
) extends BaseImage[Id] {
  def mergeWith(sourceWork: Id,
                fullText: Option[String] = None): MergedImage[Id] =
    MergedImage[Id](
      id = id,
      version = version,
      location = location,
      source = SourceWork(sourceWork),
      fullText = fullText
    )
}

case class MergedImage[Id <: WithSourceIdentifier](
  id: Id,
  version: Int,
  location: DigitalLocation,
  source: ImageSource[Id],
  fullText: Option[String] = None
) extends BaseImage[Id] {
  def toUnmerged: UnmergedImage[Id] =
    UnmergedImage[Id](
      id = id,
      version = version,
      location = location
    )
}

object MergedImage {
  implicit class IdentifiedMergedImageOps(
    mergedImage: MergedImage[Identified]) {
    def augment(inferredData: => Option[InferredData]): AugmentedImage =
      AugmentedImage(
        id = mergedImage.id,
        version = mergedImage.version,
        location = mergedImage.location,
        source = mergedImage.source,
        fullText = mergedImage.fullText,
        inferredData = inferredData
      )
  }
}

case class AugmentedImage(
  id: Identified,
  version: Int,
  location: DigitalLocation,
  source: ImageSource[Identified],
  fullText: Option[String] = None,
  inferredData: Option[InferredData] = None
) extends BaseImage[Identified]

case class InferredData(
  // We split the feature vector so that it can fit into
  // ES's dense vector type (max length 2048)
  features1: List[Float],
  features2: List[Float],
  lshEncodedFeatures: List[String]
)

object UnmergedImage {
  def apply(sourceIdentifier: SourceIdentifier,
            version: Int,
            location: DigitalLocation): UnmergedImage[Identifiable] =
    UnmergedImage(
      id = Identifiable(sourceIdentifier),
      version = version,
      location
    )
}
