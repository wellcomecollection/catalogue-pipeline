package weco.pipeline.ingestor.works.models

import weco.catalogue.internal_model.identifiers.{IdState, SourceIdentifier}
import weco.catalogue.internal_model.work.{DeletedReason, InvisibilityReason}

/** This is information we put in the Elasticsearch index because it's
 * useful when we're debugging the pipeline, but not something we'd
 * want to display in public API responses.
 *
 */
sealed trait DebugInformation {
  val sourceIdentifier: SourceIdentifier
  val sourceVersion: Int
}

object DebugInformation {
  case class Visible(
    sourceIdentifier: SourceIdentifier,
    sourceVersion: Int,
    redirectSources: Seq[IdState.Identified]
  ) extends DebugInformation

  case class Invisible(
    sourceIdentifier: SourceIdentifier,
    sourceVersion: Int,
    invisibilityReasons: List[InvisibilityReason]
  ) extends DebugInformation

  case class Redirected(
    sourceIdentifier: SourceIdentifier,
    sourceVersion: Int
  ) extends DebugInformation

  case class Deleted(
    sourceIdentifier: SourceIdentifier,
    sourceVersion: Int,
    deletedReason: DeletedReason
  ) extends DebugInformation
}
