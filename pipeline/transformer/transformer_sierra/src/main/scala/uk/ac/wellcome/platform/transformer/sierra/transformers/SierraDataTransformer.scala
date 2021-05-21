package uk.ac.wellcome.platform.transformer.sierra.transformers

import weco.catalogue.source_model.sierra.{SierraBibData, SierraBibNumber}

trait SierraDataTransformer {
  type Output

  def apply(bibData: SierraBibData): Output
}

trait SierraIdentifiedDataTransformer {
  type Output

  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output
}
