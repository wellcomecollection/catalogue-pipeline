package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

/**
  *  Trait for transforming incoming bib data to some output type
  */
trait SierraDataTransformer {

  type Output

  // Some transformers care about the bib number, others don't.
  // In the latter case, don't force the implementation to take a
  // bib ID as an argument it doesn't use.
  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output =
    apply(bibData)

  def apply(bibData: SierraBibData): Output = ???
}
