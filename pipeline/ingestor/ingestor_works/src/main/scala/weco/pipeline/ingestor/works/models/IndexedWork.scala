package weco.pipeline.ingestor.works.models

import io.circe.Json
import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.work.{WorkData, WorkState}
import weco.pipeline_storage.Indexable

sealed trait IndexedWork {
  val debug: DebugInformation
  val state: WorkState.Indexed
}

object IndexedWork {
  case class Visible(
    debug: DebugInformation.Visible,
    state: WorkState.Indexed,
    data: WorkData[DataState.Identified],
    display: Json
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

  implicit val indexable: Indexable[IndexedWork] =
    new Indexable[IndexedWork] {
      override def id(work: IndexedWork): String = work.state.canonicalId.underlying

      override def version(work: IndexedWork): Long = work.state.modifiedTime.toEpochMilli
    }
}
