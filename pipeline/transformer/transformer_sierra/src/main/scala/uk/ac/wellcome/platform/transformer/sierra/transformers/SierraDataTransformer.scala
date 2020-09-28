package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

/**
  *  Trait for transforming incoming bib data to some output type
  */
trait SierraDataTransformer {

  type Output

  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output
}
