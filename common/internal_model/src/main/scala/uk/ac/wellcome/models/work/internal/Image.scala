package uk.ac.wellcome.models.work.internal

sealed trait BaseImage[+Id <: IdState] extends HasIdState[Id] {
  val id: Id
  val location: DigitalLocation
}

case class UnmergedImage[Id <: IdState](
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

case class MergedImage[Id <: IdState](
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
}

object UnmergedImage {
  def apply(sourceIdentifier: SourceIdentifier,
            location: DigitalLocation): UnmergedImage[Unminted] =
    UnmergedImage(
      id = Identifiable(sourceIdentifier),
      location
    )
}
