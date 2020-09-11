package uk.ac.wellcome.models.work.internal

sealed trait BaseImage[
  +ImageId <: IdState.WithSourceIdentifier, State <: WorkState]
    extends HasId[ImageId] {
  val id: ImageId
  val location: DigitalLocationDeprecated
}

case class UnmergedImage[ImageId <: IdState.WithSourceIdentifier,
                         State <: WorkState](
  id: ImageId,
  version: Int,
  location: DigitalLocationDeprecated
) extends BaseImage[ImageId, State] {
  def mergeWith(canonicalWork: SourceWork[ImageId, State],
                redirectedWork: Option[SourceWork[ImageId, State]])
    : MergedImage[ImageId, State] =
    MergedImage[ImageId, State](
      id = id,
      version = version,
      location = location,
      source = SourceWorks[ImageId, State](canonicalWork, redirectedWork)
    )
}

case class MergedImage[ImageId <: IdState.WithSourceIdentifier,
                       State <: WorkState](
  id: ImageId,
  version: Int,
  location: DigitalLocationDeprecated,
  source: ImageSource[ImageId, State]
) extends BaseImage[ImageId, State] {
  def toUnmerged: UnmergedImage[ImageId, State] =
    UnmergedImage[ImageId, State](
      id = id,
      version = version,
      location = location
    )
}

object MergedImage {
  implicit class IdentifiedMergedImageOps(
    mergedImage: MergedImage[IdState.Identified, WorkState.Identified]) {
    def augment(inferredData: => Option[InferredData]): AugmentedImage =
      AugmentedImage(
        id = mergedImage.id,
        version = mergedImage.version,
        location = mergedImage.location,
        source = mergedImage.source,
        inferredData = inferredData
      )
  }
}

case class AugmentedImage(
  id: IdState.Identified,
  version: Int,
  location: DigitalLocationDeprecated,
  source: ImageSource[IdState.Identified, WorkState.Identified],
  inferredData: Option[InferredData] = None
) extends BaseImage[IdState.Identified, WorkState.Identified]

case class InferredData(
  // We split the feature vector so that it can fit into
  // ES's dense vector type (max length 2048)
  features1: List[Float],
  features2: List[Float],
  lshEncodedFeatures: List[String],
  palette: List[String]
)

object InferredData {
  def empty: InferredData = InferredData(Nil, Nil, Nil, Nil)
}

object UnmergedImage {
  def apply(sourceIdentifier: SourceIdentifier,
            version: Int,
            location: DigitalLocationDeprecated)
    : UnmergedImage[IdState.Identifiable, WorkState.Unidentified] =
    UnmergedImage(
      id = IdState.Identifiable(sourceIdentifier),
      version = version,
      location
    )
}
