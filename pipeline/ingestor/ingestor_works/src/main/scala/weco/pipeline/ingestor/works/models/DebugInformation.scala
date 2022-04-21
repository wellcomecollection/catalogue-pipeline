package weco.pipeline.ingestor.works.models

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{DeletedReason, InvisibilityReason}

/** This is information we put in the Elasticsearch index because it's
 * useful when we're debugging the pipeline, but not something we'd
 * want to display in public API responses.
 *
 */
sealed trait DebugInformation {

  // Note: this is the version of the source record in the adapter; we
  // include it for tracing a Work back to the source, but because of
  // merging in the pipeline we can't rely on it for ordering.
  val version: Int
}

object DebugInformation {
  case class Visible(
    version: Int,
    redirectSources: Seq[IdState.Identified]
  ) extends DebugInformation

  case class Invisible(
    version: Int,
    invisibilityReasons: List[InvisibilityReason]
  ) extends DebugInformation

  case class Redirected(
    version: Int
  ) extends DebugInformation

  case class Deleted(
    version: Int,
    deletedReason: DeletedReason
  ) extends DebugInformation
}
