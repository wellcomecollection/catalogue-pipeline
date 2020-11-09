package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

trait SierraDataTransformer {
  type Output

  def apply(bibData: SierraBibData): Output
}

trait SierraIdentifiedDataTransformer {
  type Output

  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output
}
