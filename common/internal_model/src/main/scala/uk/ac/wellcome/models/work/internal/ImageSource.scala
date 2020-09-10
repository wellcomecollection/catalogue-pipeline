package uk.ac.wellcome.models.work.internal

sealed trait ImageSource[
  ImageId <: IdState.WithSourceIdentifier, DataId <: IdState] {
  val id: ImageId
  val ontologyType: String
}

case class SourceWorks[ImageId <: IdState.WithSourceIdentifier,
                       DataId <: IdState](
  canonicalWork: SourceWork[ImageId, DataId],
  redirectedWork: Option[SourceWork[ImageId, DataId]]
) extends ImageSource[ImageId, DataId] {
  override val id = canonicalWork.id
  override val ontologyType: String = canonicalWork.ontologyType
}

case class SourceWork[ImageId <: IdState.WithSourceIdentifier,
                      DataId <: IdState](
  id: ImageId,
  data: WorkData[DataId, ImageId],
  ontologyType: String = "Work",
)
