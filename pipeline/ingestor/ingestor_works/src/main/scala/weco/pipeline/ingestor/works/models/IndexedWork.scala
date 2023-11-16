package weco.pipeline.ingestor.works.models

import io.circe.Json
import weco.catalogue.internal_model.identifiers.IdState
import weco.pipeline.ingestor.common.models.WorkQueryableValues
import weco.pipeline_storage.Indexable

sealed trait IndexedWork {
  val debug: DebugInformation
}

object IndexedWork {
  case class Visible(
    debug: DebugInformation.Visible,
    display: Json,
    query: WorkQueryableValues,
    aggregatableValues: WorkAggregatableValues,
    filterableValues: WorkFilterableValues
  ) extends IndexedWork

  case class Redirected(
    debug: DebugInformation.Redirected,
    redirectTarget: IdState.Identified
  ) extends IndexedWork

  case class Invisible(debug: DebugInformation.Invisible) extends IndexedWork

  case class Deleted(debug: DebugInformation.Deleted) extends IndexedWork

  implicit val indexable: Indexable[IndexedWork] =
    new Indexable[IndexedWork] {
      override def id(work: IndexedWork): String =
        work.debug.source.id.underlying

      override def version(work: IndexedWork): Long =
        work.debug.source.modifiedTime.toEpochMilli
    }
}
