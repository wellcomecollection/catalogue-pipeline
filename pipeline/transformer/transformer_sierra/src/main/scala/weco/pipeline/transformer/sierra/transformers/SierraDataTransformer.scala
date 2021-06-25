package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.source_model.sierra.SierraBibData
import weco.catalogue.source_model.sierra.identifiers.SierraBibNumber

trait SierraDataTransformer {
  type Output

  def apply(bibData: SierraBibData): Output
}

trait SierraIdentifiedDataTransformer {
  type Output

  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output
}
