package uk.ac.wellcome.models.work.internal

sealed trait BaseImage[+Id] extends HasIdState[Id] {
  val id: Id
  val location: DigitalLocation
}

sealed trait ImageState
sealed trait Merged extends ImageState
sealed trait Unmerged extends ImageState

case class UnmergedImage(sourceIdentifier: SourceIdentifier,
                         location: DigitalLocation)
    extends BaseImage[Unminted]
    with Unmerged {
  val id: Identifiable = Identifiable(sourceIdentifier)
}

case class MergedImage[+Id](id: Id, location: DigitalLocation, data: ImageData)
    extends BaseImage[Id]
    with Merged

case class ImageData(title: Option[String], parentWorks: List[String])
