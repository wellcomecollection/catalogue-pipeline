package weco.pipeline.transformer.sierra.transformers

import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData

// Populate work:edition
//
// Field 250 is used for this. In the very rare case where multiple 250 fields
// are found, they are concatenated into a single string
object SierraEdition extends SierraDataTransformer with SierraQueryOps {

  type Output = Option[String]

  def apply(bibData: SierraBibData) =
    bibData
      .subfieldsWithTag("250" -> "a")
      .contentString(" ")
}
