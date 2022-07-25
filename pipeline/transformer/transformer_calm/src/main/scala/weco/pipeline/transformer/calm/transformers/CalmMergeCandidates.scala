package weco.pipeline.transformer.calm.transformers

import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.MergeCandidate
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.models.CalmRecordOps
import weco.pipeline.transformer.identifiers.SourceIdentifierValidation._

object CalmMergeCandidates extends CalmRecordOps {
  def apply(record: CalmRecord): List[MergeCandidate[IdState.Identifiable]] =
    List(
      sierraMergeCandidate(record)
    ).flatten

  private def sierraMergeCandidate(record: CalmRecord) =
    record
      .get("BNumber")
      .flatMap { id =>
        SourceIdentifier(
          identifierType = IdentifierType.SierraSystemNumber,
          ontologyType = "Work",
          value = id
        ).validatedWithWarning
      }
      .map { sourceIdentifier =>
        MergeCandidate(
          identifier = sourceIdentifier,
          reason = "CALM/Sierra harvest work"
        )
      }
}
