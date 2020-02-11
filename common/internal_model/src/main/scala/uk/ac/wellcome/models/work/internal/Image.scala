package uk.ac.wellcome.models.work.internal

sealed trait BaseImage[+Id] extends HasIdState[Id] {
  val id: Id
  val location: DigitalLocation
}

case class UnmergedImage[+Id](id: Id, location: DigitalLocation)
    extends BaseImage[Id]

case class MergedImage[+Id](id: Id, location: DigitalLocation, data: ImageData)
    extends BaseImage[Id]

case class ImageData(title: Option[String], parentWorks: List[String])

object UnmergedImage {
  def apply(sourceIdentifier: SourceIdentifier,
            location: DigitalLocation): UnmergedImage[Unminted] =
    UnmergedImage(
      id = Identifiable(sourceIdentifier),
      location
    )
}
