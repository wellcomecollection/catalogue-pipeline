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
    sierraMergeCandidate(record).toList ++ miroMergeCandidates(record)

  private def miroMergeCandidates(record: CalmRecord) =
    // The internal field "Wheels" is mapped to "MiroID"
    record
      .getList("Wheels")
      .flatMap { miroId =>
        SourceIdentifier(
          identifierType = IdentifierType.MiroImageNumber,
          ontologyType = "Work",
          value = miroId
        ).validatedWithWarning
      }
      .map { sourceIdentifier =>
        MergeCandidate(
          identifier = sourceIdentifier,
          reason = "CALM/Miro work"
        )
      }

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
