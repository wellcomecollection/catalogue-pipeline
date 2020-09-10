package uk.ac.wellcome.models.work.internal

sealed trait BaseImage[
  +ImageId <: IdState.WithSourceIdentifier, DataId <: IdState]
    extends HasId[ImageId] {
  val id: ImageId
  val location: DigitalLocationDeprecated
}

case class UnmergedImage[ImageId <: IdState.WithSourceIdentifier,
                         DataId <: IdState](
  id: ImageId,
  version: Int,
  location: DigitalLocationDeprecated
) extends BaseImage[ImageId, DataId] {
  def mergeWith(canonicalWork: SourceWork[ImageId, DataId],
                redirectedWork: Option[SourceWork[ImageId, DataId]])
    : MergedImage[ImageId, DataId] =
    MergedImage[ImageId, DataId](
      id = id,
      version = version,
      location = location,
      source = SourceWorks[ImageId, DataId](canonicalWork, redirectedWork)
    )
}

case class MergedImage[ImageId <: IdState.WithSourceIdentifier,
                       DataId <: IdState](
  id: ImageId,
  version: Int,
  location: DigitalLocationDeprecated,
  source: ImageSource[ImageId, DataId]
) extends BaseImage[ImageId, DataId] {
  def toUnmerged: UnmergedImage[ImageId, DataId] =
    UnmergedImage[ImageId, DataId](
      id = id,
      version = version,
      location = location
    )
}

object MergedImage {
  implicit class IdentifiedMergedImageOps(
    mergedImage: MergedImage[IdState.Identified, IdState.Minted]) {
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
  source: ImageSource[IdState.Identified, IdState.Minted],
  inferredData: Option[InferredData] = None
) extends BaseImage[IdState.Identified, IdState.Minted]

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
