package uk.ac.wellcome.models.work.internal

sealed trait BaseImage[+Id <: WithSourceIdentifier, DataId <: IdState] extends HasIdState[Id] {
  val id: Id
  val location: DigitalLocation
}

case class UnmergedImage[Id <: WithSourceIdentifier, DataId <: IdState](
  id: Id,
  version: Int,
  location: DigitalLocation
) extends BaseImage[Id, DataId] {
  def mergeWith(canonicalWork: SourceWork[Id, DataId], redirectedWork: Option[SourceWork[Id, DataId]]): MergedImage[Id, DataId] =
    MergedImage[Id, DataId](
      id = id,
      version = version,
      location = location,
      source = SourceWorks[Id, DataId](canonicalWork,redirectedWork)
    )
}

case class MergedImage[Id <: WithSourceIdentifier, DataId <: IdState](
                                                                               id: Id,
                                                                               version: Int,
                                                                               location: DigitalLocation,
                                                                               source: ImageSource[Id, DataId]
) extends BaseImage[Id, DataId] {
  def toUnmerged: UnmergedImage[Id, DataId] =
    UnmergedImage[Id, DataId](
      id = id,
      version = version,
      location = location
    )
}

object MergedImage {
  implicit class IdentifiedMergedImageOps(
    mergedImage: MergedImage[Identified, Minted]) {
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
  id: Identified,
  version: Int,
  location: DigitalLocation,
  source: ImageSource[Identified, Minted],
  inferredData: Option[InferredData] = None
) extends BaseImage[Identified, Minted]

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
            location: DigitalLocation): UnmergedImage[Identifiable, Unminted] =
    UnmergedImage(
      id = Identifiable(sourceIdentifier),
      version = version,
      location
    )
}
