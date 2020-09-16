package uk.ac.wellcome.models.work.internal

sealed trait ImageSource[State <: MinterState] {
  val id: State#Id
  val ontologyType: String
}

case class SourceWorks[State <: MinterState](
  canonicalWork: SourceWork[State],
  redirectedWork: Option[SourceWork[State]]
) extends ImageSource[State] {
  override val id = canonicalWork.id
  override val ontologyType: String = canonicalWork.ontologyType
}

case class SourceWork[State <: MinterState](
  id: State#Id,
  data: WorkData[State],
  ontologyType: String = "Work",
)

object SourceWork {

  implicit class UnidentifiedWorkToSourceWork(
    work: Work[WorkState.Unidentified]) {

    def toSourceWork: SourceWork[MinterState.Unminted] =
      SourceWork[MinterState.Unminted](
        id = IdState.Identifiable(work.state.sourceIdentifier),
        data = work.data
      )
  }

  implicit class IdentifiedWorkToSourceWork(work: Work[WorkState.Identified]) {

    def toSourceWork: SourceWork[MinterState.Minted] =
      SourceWork[MinterState.Minted](
        id = IdState.Identified(
          sourceIdentifier = work.state.sourceIdentifier,
          canonicalId = work.state.canonicalId
        ),
        data = work.data
      )
  }
}
