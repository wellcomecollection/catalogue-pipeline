package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.source_model.sierra.SierraBibData
import weco.sierra.models.SierraQueryOps

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
