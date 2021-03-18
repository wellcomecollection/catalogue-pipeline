package weco.catalogue.internal_model.image

import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.work.{Work, WorkData, WorkState}

sealed trait ImageSource {
  val id: IdState.Identified
  val version: Int
}

case class ParentWorks(
  canonicalWork: ParentWork,
  redirectedWork: Option[ParentWork] = None
) extends ImageSource {
  override val id = canonicalWork.id
  override val version =
    canonicalWork.version + redirectedWork.map(_.version).getOrElse(0)
}

case class ParentWork(
  id: IdState.Identified,
  data: WorkData[DataState.Identified],
  version: Int,
)

object ParentWork {

  implicit class MergedToParentWork(work: Work[WorkState.Merged]) {
    def toParentWork: ParentWork =
      ParentWork(
        id = IdState
          .Identified(work.state.canonicalId, work.state.sourceIdentifier),
        data = work.data,
        version = work.version
      )
  }

  implicit class IdentifiedToParentWork(work: Work[WorkState.Identified]) {
    def toParentWork: ParentWork =
      ParentWork(
        id = IdState.Identified(
          sourceIdentifier = work.state.sourceIdentifier,
          canonicalId = work.state.canonicalId
        ),
        data = work.data,
        version = work.version
      )
  }
}
