package uk.ac.wellcome.platform.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.ReferenceNumber
import weco.catalogue.source_model.sierra.SierraBibData

object SierraReferenceNumber extends SierraDataTransformer {
  override type Output = Option[ReferenceNumber]

  override def apply(bibData: SierraBibData): Option[ReferenceNumber] =
    SierraIconographicNumber(bibData).map { ReferenceNumber(_) }
}
