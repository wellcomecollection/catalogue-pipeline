package weco.pipeline.ingestor.works.models

import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.work.{WorkData, WorkState}

sealed trait IndexedWork

object IndexedWork {
  case class Visible(
    data: WorkData[DataState.Identified],
    state: WorkState.Indexed,

    debug: DebugInformation.Visible,
    display: DisplayWork
  ) extends IndexedWork

  case class Redirected(
    redirectTarget: IdState.Identified,
    state: WorkState.Indexed,

    debug: DebugInformation.Redirected
  ) extends IndexedWork

  case class Invisible(
    data: WorkData[DataState.Identified],
    state: WorkState.Indexed,

    debug: DebugInformation.Invisible
  ) extends IndexedWork

  case class Deleted(
    state: WorkState.Indexed,

    debug: DebugInformation.Deleted
  ) extends IndexedWork
}
