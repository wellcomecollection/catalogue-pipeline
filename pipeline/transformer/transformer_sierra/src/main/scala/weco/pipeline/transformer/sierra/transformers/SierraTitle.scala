package weco.pipeline.transformer.sierra.transformers

import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.pipeline.transformer.marc_common.transformers.MarcTitle
import weco.sierra.models.data.SierraBibData

object SierraTitle
    extends SierraDataTransformer
    with SierraMarcDataConversions {

  type Output = Option[String]

  // Populate wwork:title.  The rules are as follows:
  //
  //    Join MARC field 245 subfields ǂa, ǂb, ǂc, ǂh, ǂn and ǂp with a space.
  //    They should be joined in the same order as the original subfields.
  //
  //    Remove anything in square brackets from ǂh; this is legacy data we don't
  //    want to expose.
  //
  // MARC 245 is non-repeatable, as are subfields ǂa, ǂb and ǂc.  However,
  // there are records in Wellcome's catalogue that repeat them, so we deviate
  // from the MARC spec here.
  //
  // http://www.loc.gov/marc/bibliographic/bd245.html
  def apply(bibData: SierraBibData): Option[String] = {
    MarcTitle(bibData)
  }
}
