package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.work.Format
import weco.pipeline.transformer.sierra.data.SierraMaterialTypes
import weco.sierra.models.data.SierraBibData

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
  def apply(bibData: SierraBibData): Option[Format] =
    bibData.materialType.flatMap {
      t =>
        SierraMaterialTypes.fromCode(t.code)
    }
}
