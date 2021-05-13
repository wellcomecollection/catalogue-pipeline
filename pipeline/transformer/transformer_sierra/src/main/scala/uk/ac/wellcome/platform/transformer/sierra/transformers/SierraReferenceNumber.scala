package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData
import weco.catalogue.internal_model.identifiers.ReferenceNumber

object SierraReferenceNumber extends SierraDataTransformer {
  override type Output = Option[ReferenceNumber]

  override def apply(bibData: SierraBibData): Option[ReferenceNumber] =
    SierraIconographicNumber(bibData).map { ReferenceNumber(_) }
}
