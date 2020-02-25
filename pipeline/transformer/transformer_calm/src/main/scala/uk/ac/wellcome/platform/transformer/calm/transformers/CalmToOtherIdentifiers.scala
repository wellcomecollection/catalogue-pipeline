package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.transformer.calm.CalmIdentifierTypes
import uk.ac.wellcome.platform.transformer.calm.models.CalmRecord

object CalmToOtherIdentifiers {
  def transform(calmRecord: CalmRecord): List[SourceIdentifier] =
    (List(
      ("RefNo", CalmIdentifierTypes.refNo),
      ("AltRefNo", CalmIdentifierTypes.altRefNo)) flatMap {
      case (key, identifierType) => {
        calmRecord.data.get(key) map {
          case head :: Nil =>
            Some(
              SourceIdentifier(
                value = head,
                identifierType = identifierType,
                ontologyType = "IdentifierType"))
          case _ => None
        }
      }
    }).flatten
}
