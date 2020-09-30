package uk.ac.wellcome.models.work.internal

sealed trait ImageSource[State <: DataState] {
  val id: State#Id
}

case class SourceWorks[State <: DataState](
  canonicalWork: SourceWork[State],
  redirectedWork: Option[SourceWork[State]]
) extends ImageSource[State] {
  override val id = canonicalWork.id
}

case class SourceWork[State <: DataState](
  id: State#Id,
  data: WorkData[State]
)

object SourceWork {

  implicit class SourceWorkToSourceWork(work: Work[WorkState.Source]) {

    def toSourceWork: SourceWork[DataState.Unidentified] =
      SourceWork[DataState.Unidentified](
        id = IdState.Identifiable(work.state.sourceIdentifier),
        data = work.data
      )
  }

  implicit class IdentifiedWorkToSourceWork(work: Work[WorkState.Identified]) {

    def toSourceWork: SourceWork[DataState.Identified] =
      SourceWork[DataState.Identified](
        id = IdState.Identified(
          sourceIdentifier = work.state.sourceIdentifier,
          canonicalId = work.state.canonicalId
        ),
        data = work.data
      )
  }
}
