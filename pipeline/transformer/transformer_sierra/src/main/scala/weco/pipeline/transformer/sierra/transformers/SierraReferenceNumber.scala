package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.ReferenceNumber
import weco.sierra.models.data.SierraBibData

object SierraReferenceNumber extends SierraDataTransformer {
  override type Output = Option[ReferenceNumber]

  override def apply(bibData: SierraBibData): Option[ReferenceNumber] =
    SierraIconographicNumber(bibData).map { ReferenceNumber(_) }
}
