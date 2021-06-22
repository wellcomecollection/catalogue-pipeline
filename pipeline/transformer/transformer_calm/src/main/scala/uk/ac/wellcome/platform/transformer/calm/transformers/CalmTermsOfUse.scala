package uk.ac.wellcome.platform.transformer.calm.transformers

import weco.catalogue.internal_model.work.TermsOfUse
import weco.catalogue.source_model.calm.CalmRecord

object CalmTermsOfUse {
  def apply(record: CalmRecord): Option[TermsOfUse] =
    throw new NotImplementedError(s"record = $record")
}
