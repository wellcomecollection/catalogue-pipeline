package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.data.SierraMaterialTypes
import weco.catalogue.internal_model.work.Format
import weco.catalogue.source_model.sierra.SierraBibData

object SierraFormat extends SierraDataTransformer {

  type Output = Option[Format]

  /* Populate wwork:format. Rules:
   *
   * 1. For all bibliographic records use "materialType"
   * 2. Platform "id" is populated from "code"
   * 3. Platform "label" is populated from "value"
   *
   * Example:
   *  "format": {
   *     "id": "e-book",
   *     "type": "Format",
   *     "label": "E-books"
   *     },
   *
   * Note: will map to a controlled vocabulary terms in future
   */
  def apply(bibData: SierraBibData) =
    bibData.materialType.map { t =>
      SierraMaterialTypes.fromCode(t.code)
    }
}
