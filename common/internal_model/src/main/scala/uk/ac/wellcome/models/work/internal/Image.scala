package uk.ac.wellcome.models.work.internal

sealed trait BaseImage[
  +ImageId <: IdState.WithSourceIdentifier, MaybeId <: IdState]
    extends HasId[ImageId] {
  val id: ImageId
  val location: DigitalLocationDeprecated
}

case class UnmergedImage[ImageId <: IdState.WithSourceIdentifier,
                         MaybeId <: IdState](
  id: ImageId,
  version: Int,
  location: DigitalLocationDeprecated
) extends BaseImage[ImageId, MaybeId] {
  def mergeWith(canonicalWork: SourceWork[ImageId, MaybeId],
                redirectedWork: Option[SourceWork[ImageId, MaybeId]])
    : MergedImage[ImageId, MaybeId] =
    MergedImage[ImageId, MaybeId](
      id = id,
      version = version,
      location = location,
      source = SourceWorks[ImageId, MaybeId](canonicalWork, redirectedWork)
    )
}

case class MergedImage[ImageId <: IdState.WithSourceIdentifier,
                       MaybeId <: IdState](
  id: ImageId,
  version: Int,
  location: DigitalLocationDeprecated,
  source: ImageSource[ImageId, MaybeId]
) extends BaseImage[ImageId, MaybeId] {
  def toUnmerged: UnmergedImage[ImageId, MaybeId] =
    UnmergedImage[ImageId, MaybeId](
      id = id,
      version = version,
      location = location
    )
}

object MergedImage {
  implicit class IdentifiedMergedImageOps(
    mergedImage: MergedImage[IdState.Identified, IdState.Identified]) {
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
  source: ImageSource[IdState.Identified, IdState.Identified],
  inferredData: Option[InferredData] = None
) extends BaseImage[IdState.Identified, IdState.Identified]

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
    : UnmergedImage[IdState.Identifiable, IdState.Unminted] =
    UnmergedImage(
      id = IdState.Identifiable(sourceIdentifier),
      version = version,
      location
    )
}
