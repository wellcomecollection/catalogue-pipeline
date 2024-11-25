package weco.pipeline.transformer.calm.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Concept, Subject}
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.NormaliseText
import weco.pipeline.transformer.calm.models.CalmRecordOps
import weco.pipeline.transformer.identifiers.LabelDerivedIdentifiers

object CalmSubjects extends CalmRecordOps with LabelDerivedIdentifiers {
  def apply(record: CalmRecord): List[Subject[IdState.Unminted]] =
    record.getList("Subject").map {
      label =>
        val normalisedLabel =
          NormaliseText(label, whitelist = NormaliseText.none)

        val labelDerivedId = identifierFromText(
          label = normalisedLabel,
          ontologyType = "Concept"
        )

        Subject(
          label = normalisedLabel,
          concepts = List(
            Concept(
              id = labelDerivedId,
              label = normalisedLabel
            )
          ),
          id = labelDerivedId
        )
    }
}
