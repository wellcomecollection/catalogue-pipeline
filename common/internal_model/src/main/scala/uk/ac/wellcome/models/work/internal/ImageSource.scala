package uk.ac.wellcome.models.work.internal

sealed trait ImageSource[ImageId <: Id.WithSourceIdentifier, DataId <: Id] {
  val id: ImageId
  val ontologyType: String
}

case class SourceWorks[ImageId <: Id.WithSourceIdentifier, DataId <: Id](
  canonicalWork: SourceWork[ImageId, DataId],
  redirectedWork: Option[SourceWork[ImageId, DataId]]
) extends ImageSource[ImageId, DataId] {
  override val id = canonicalWork.id
  override val ontologyType: String = canonicalWork.ontologyType
}

case class SourceWork[ImageId <: Id.WithSourceIdentifier, DataId <: Id](
  id: ImageId,
  data: WorkData[DataId, ImageId],
  ontologyType: String = "Work",
)
