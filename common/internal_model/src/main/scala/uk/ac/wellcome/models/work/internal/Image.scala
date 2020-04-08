package uk.ac.wellcome.models.work.internal

sealed trait BaseImage[+Id <: pff] extends HasIdState[Id] {
  val id: Id
  val location: DigitalLocation
}

case class UnmergedImage[Id <: pff](
  id: Id,
  location: DigitalLocation
) extends BaseImage[Id] {
  def mergeWith(parentWork: Id,
                fullText: Option[String] = None): MergedImage[Id] =
    MergedImage[Id](
      id = id,
      location = location,
      parentWork = parentWork,
      fullText = fullText
    )
}

case class MergedImage[Id <: pff](
  id: Id,
  location: DigitalLocation,
  parentWork: Id,
  fullText: Option[String] = None
) extends BaseImage[Id] {
  def toUnmerged: UnmergedImage[Id] =
    UnmergedImage[Id](
      id = id,
      location = location
    )

  def augment(inferredData: => Option[InferredData]): AugmentedImage[Id] =
    AugmentedImage[Id](
      id = id,
      location = location,
      parentWork = parentWork,
      fullText = fullText,
      inferredData = inferredData
    )
}

case class AugmentedImage[Id <: pff](
  id: Id,
  location: DigitalLocation,
  parentWork: Id,
  fullText: Option[String] = None,
  inferredData: Option[InferredData] = None
) extends BaseImage[Id]

case class InferredData(
  // We split the feature vector so that it can fit into
  // ES's dense vector type (max length 2048)
  features1: List[Float],
  features2: List[Float],
  lshEncodedFeatures: List[String]
)

object UnmergedImage {
  def apply(sourceIdentifier: SourceIdentifier,
            location: DigitalLocation): UnmergedImage[Identifiable] =
    UnmergedImage(
      id = Identifiable(sourceIdentifier),
      location
    )
}
