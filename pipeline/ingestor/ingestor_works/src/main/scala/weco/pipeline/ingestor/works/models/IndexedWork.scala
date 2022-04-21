package weco.pipeline.ingestor.works.models

import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.work.{WorkData, WorkState}

sealed trait IndexedWork

object IndexedWork {
  case class Visible(
    debug: DebugInformation.Visible,
    state: WorkState.Indexed,
    data: WorkData[DataState.Identified],
    display: DisplayWork
  ) extends IndexedWork

  case class Redirected(
    debug: DebugInformation.Redirected,
    state: WorkState.Indexed,
    redirectTarget: IdState.Identified
  ) extends IndexedWork

  case class Invisible(
    debug: DebugInformation.Invisible,
    state: WorkState.Indexed,
    data: WorkData[DataState.Identified]
  ) extends IndexedWork

  case class Deleted(
    debug: DebugInformation.Deleted,
    state: WorkState.Indexed
  ) extends IndexedWork
}
