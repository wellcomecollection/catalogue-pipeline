package weco.pipeline.transformer.calm.transformers

import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.MergeCandidate
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.models.CalmRecordOps

object CalmMergeCandidates extends CalmRecordOps {
  def apply(record: CalmRecord): List[MergeCandidate[IdState.Identifiable]] =
    sierraMergeCandidate(record).toList ++ miroMergeCandidates(record)

  private def miroMergeCandidates(record: CalmRecord) =
    // The internal field "Wheels" is mapped to "MiroID"
    record.getList("Wheels").map { miroIdentifier =>
      MergeCandidate(
        IdState.Identifiable(
          SourceIdentifier(
            identifierType = IdentifierType.MiroImageNumber,
            ontologyType = "Work",
            value = miroIdentifier
          )
        )
      )
    }

  private def sierraMergeCandidate(record: CalmRecord) =
    record.get("BNumber").map { bNumber =>
      MergeCandidate(
        IdState.Identifiable(
          SourceIdentifier(
            identifierType = IdentifierType.SierraSystemNumber,
            ontologyType = "Work",
            value = bNumber
          )
        )
      )
    }
}
