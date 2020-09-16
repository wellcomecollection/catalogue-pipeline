package uk.ac.wellcome.models.work.internal

sealed trait ImageSource[
  ImageId <: IdState.WithSourceIdentifier, MaybeId <: IdState] {
  val id: ImageId
  val ontologyType: String
}

case class SourceWorks[ImageId <: IdState.WithSourceIdentifier,
                       MaybeId <: IdState](
  canonicalWork: SourceWork[ImageId, MaybeId],
  redirectedWork: Option[SourceWork[ImageId, MaybeId]]
) extends ImageSource[ImageId, MaybeId] {
  override val id = canonicalWork.id
  override val ontologyType: String = canonicalWork.ontologyType
}

case class SourceWork[ImageId <: IdState.WithSourceIdentifier,
                      MaybeId <: IdState](
  id: ImageId,
  data: WorkData[MaybeId, ImageId],
  ontologyType: String = "Work",
)

object SourceWork {

  implicit class UnidentifiedWorkToSourceWork(
    work: Work[WorkState.Unidentified]) {

    def toSourceWork: SourceWork[IdState.Identifiable, IdState.Unminted] =
      SourceWork(
        id = IdState.Identifiable(work.state.sourceIdentifier),
        data = work.data
      )
  }

  implicit class IdentifiedWorkToSourceWork(work: Work[WorkState.Identified]) {

    def toSourceWork: SourceWork[IdState.Identified, IdState.Minted] =
      SourceWork(
        id = IdState.Identified(
          sourceIdentifier = work.state.sourceIdentifier,
          canonicalId = work.state.canonicalId
        ),
        data = work.data
      )
  }
}
