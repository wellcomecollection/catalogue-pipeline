package weco.pipeline.transformer.sierra.transformers

import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber

trait SierraDataTransformer {
  type Output

  def apply(bibData: SierraBibData): Output
}

trait SierraIdentifiedDataTransformer {
  type Output

  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output
}
