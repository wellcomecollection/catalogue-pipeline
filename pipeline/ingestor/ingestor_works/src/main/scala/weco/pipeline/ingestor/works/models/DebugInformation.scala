package weco.pipeline.ingestor.works.models

import java.time.Instant
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  IdState,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{DeletedReason, InvisibilityReason}

/** This is information we put in the Elasticsearch index because it's useful
  * when we're debugging the pipeline, but not something we'd want to display in
  * public API responses.
  */
case class SourceWorkDebugInformation(
  id: CanonicalId,
  identifier: SourceIdentifier,
  version: Int,
  modifiedTime: Instant
)

sealed trait DebugInformation {
  val source: SourceWorkDebugInformation

  val mergedTime: Instant
  val indexedTime: Instant
}

object DebugInformation {
  case class Visible(
    source: SourceWorkDebugInformation,
    mergedTime: Instant,
    indexedTime: Instant,
    redirectSources: Seq[IdState.Identified]
  ) extends DebugInformation

  case class Invisible(
    source: SourceWorkDebugInformation,
    mergedTime: Instant,
    indexedTime: Instant,
    invisibilityReasons: List[InvisibilityReason]
  ) extends DebugInformation

  case class Redirected(
    source: SourceWorkDebugInformation,
    mergedTime: Instant,
    indexedTime: Instant
  ) extends DebugInformation

  case class Deleted(
    source: SourceWorkDebugInformation,
    mergedTime: Instant,
    indexedTime: Instant,
    deletedReason: DeletedReason
  ) extends DebugInformation
}
