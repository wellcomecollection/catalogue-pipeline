package weco.pipeline.transformer.sierra.transformers

import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.models.data.SierraBibData
import weco.pipeline.transformer.marc_common.transformers.MarcEdition

// Populate work:edition
//
// Field 250 is used for this. In the very rare case where multiple 250 fields
// are found, they are concatenated into a single string
object SierraEdition
    extends SierraDataTransformer
    with SierraMarcDataConversions {

  type Output = Option[String]
  //  U think there may be a nicer way to do subfieldswithtag-type transformations.
  // perhaps we should fetch fields with a given subfield, then render the field as a string.

  def apply(bibData: SierraBibData) = MarcEdition(bibData)
}
