package uk.ac.wellcome.models.work.internal

sealed trait ImageSource[State <: DataState] {
  val id: State#Id
}

case class SourceWorks[State <: DataState](
  canonicalWork: SourceWork[State],
  redirectedWork: Option[SourceWork[State]],
) extends ImageSource[State] {
  override val id = canonicalWork.id
}

case class SourceWork[State <: DataState](
  id: State#Id,
  data: WorkData[State],
  version: Int,
)

object SourceWork {

  implicit class SourceWorkToSourceWork(work: Work[WorkState.Source]) {

    def toSourceWork: SourceWork[DataState.Unidentified] =
      SourceWork[DataState.Unidentified](
        id = IdState.Identifiable(work.state.sourceIdentifier),
        data = work.data,
        version = work.version
      )
  }

  implicit class MergedWorkToSourceWork(work: Work[WorkState.Merged]) {

    def toSourceWork: SourceWork[DataState.Unidentified] =
      SourceWork[DataState.Unidentified](
        id = IdState.Identifiable(work.state.sourceIdentifier),
        data = work.data,
        version = work.version
      )
  }

  implicit class IdentifiedWorkToSourceWork(work: Work[WorkState.Identified]) {

    def toSourceWork: SourceWork[DataState.Identified] =
      SourceWork[DataState.Identified](
        id = IdState.Identified(
          sourceIdentifier = work.state.sourceIdentifier,
          canonicalId = work.state.canonicalId
        ),
        data = work.data,
        version = work.version
      )
  }
}
