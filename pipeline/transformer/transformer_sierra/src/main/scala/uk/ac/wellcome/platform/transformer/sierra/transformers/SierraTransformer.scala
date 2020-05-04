package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

/**
  *  Trait for transforming incoming bib data to some output type
  */
trait SierraTransformer {

  type Output

  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output
}
