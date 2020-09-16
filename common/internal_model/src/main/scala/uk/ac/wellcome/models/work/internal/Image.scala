package uk.ac.wellcome.models.work.internal

sealed trait BaseImage[+State <: MinterState]
    extends HasId[State#Id] {
  val id: State#Id
  val location: DigitalLocationDeprecated
}

case class UnmergedImage[State <: MinterState](
  id: State#Id,
  version: Int,
  location: DigitalLocationDeprecated
) extends BaseImage[State] {
  def mergeWith(canonicalWork: SourceWork[State],
                redirectedWork: Option[SourceWork[State]])
    : MergedImage[State] =
    MergedImage[State](
      id = id,
      version = version,
      location = location,
      source = SourceWorks[State](canonicalWork, redirectedWork)
    )
}

case class MergedImage[State <: MinterState](
  id: State#Id,
  version: Int,
  location: DigitalLocationDeprecated,
  source: ImageSource[State]
) extends BaseImage[State] {
  def toUnmerged: UnmergedImage[State] =
    UnmergedImage[State](
      id = id,
      version = version,
      location = location
    )
}

object MergedImage {
  implicit class IdentifiedMergedImageOps(
    mergedImage: MergedImage[MinterState.Minted]) {
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
  source: ImageSource[MinterState.Minted],
  inferredData: Option[InferredData] = None
) extends BaseImage[MinterState.Minted]

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
    : UnmergedImage[MinterState.Unminted] =
    UnmergedImage[MinterState.Unminted](
      id = IdState.Identifiable(sourceIdentifier),
      version = version,
      location
    )
}
