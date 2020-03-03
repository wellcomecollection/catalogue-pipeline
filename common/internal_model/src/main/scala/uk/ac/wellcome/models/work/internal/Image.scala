package uk.ac.wellcome.models.work.internal

sealed trait BaseImage[+Id <: IdState] extends HasIdState[Id] {
  val id: Id
  val location: DigitalLocation
}

case class UnmergedImage[Id <: IdState](
  id: Id,
  location: DigitalLocation
) extends BaseImage[Id] {
  def mergeWith(data: => ImageData[Id]): MergedImage[Id] =
    MergedImage[Id](
      id = id,
      location = location,
      data = data
    )
}

case class MergedImage[Id <: IdState](
  id: Id,
  location: DigitalLocation,
  data: ImageData[Id]
) extends BaseImage[Id] {
  def toUnmerged: UnmergedImage[Id] =
    UnmergedImage[Id](
      id = id,
      location = location
    )
}

case class ImageData[Id <: IdState](
  parentWork: Id,
  fullText: Option[String] = None
)

object UnmergedImage {
  def apply(sourceIdentifier: SourceIdentifier,
            location: DigitalLocation): UnmergedImage[Unminted] =
    UnmergedImage(
      id = Identifiable(sourceIdentifier),
      location
    )
}
