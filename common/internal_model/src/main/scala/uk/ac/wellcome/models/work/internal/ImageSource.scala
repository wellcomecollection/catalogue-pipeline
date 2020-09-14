package uk.ac.wellcome.models.work.internal

sealed trait ImageSource[
  ImageId <: IdState.WithSourceIdentifier, State <: WorkState] {
  val id: ImageId
  val ontologyType: String
}

case class SourceWorks[ImageId <: IdState.WithSourceIdentifier,
                       State <: WorkState](
  canonicalWork: SourceWork[ImageId, State],
  redirectedWork: Option[SourceWork[ImageId, State]]
) extends ImageSource[ImageId, State] {
  override val id = canonicalWork.id
  override val ontologyType: String = canonicalWork.ontologyType
}

case class SourceWork[ImageId <: IdState.WithSourceIdentifier,
                      State <: WorkState](
  id: ImageId,
  data: WorkData[State, ImageId],
  ontologyType: String = "Work",
)

object SourceWork {

  implicit class UnidentifiedWorkToSourceWork(work: Work[WorkState.Unidentified]) {

    def toSourceWork: SourceWork[IdState.Identifiable, WorkState.Unidentified] =
      SourceWork(
        id = IdState.Identifiable(work.state.sourceIdentifier),
        data = work.data
      )
  }

  implicit class IdentifiedWorkToSourceWork(work: Work[WorkState.Identified]) {

    def toSourceWork: SourceWork[IdState.Identified, WorkState.Identified] =
      SourceWork(
        id = IdState.Identified(
          sourceIdentifier = work.state.sourceIdentifier,
          canonicalId = work.state.canonicalId
        ),
        data = work.data
      )
  }
}
