package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.transformer.calm.CalmIdentifierTypes
import uk.ac.wellcome.platform.transformer.calm.models.CalmRecord
import uk.ac.wellcome.platform.transformer.calm.service.{
  FieldMissingError,
  MultipleFieldsFoundError,
  TransformableError
}

object CalmToSourceIdentifier {
  def transform(calmRecord: CalmRecord): Option[List[SourceIdentifier]] =
    calmRecord.data.get("RecordID") map { list =>
      list.map(
        id =>
          SourceIdentifier(
            value = id,
            identifierType = CalmIdentifierTypes.recordId,
            ontologyType = "IdentifierType"))
    }
}
