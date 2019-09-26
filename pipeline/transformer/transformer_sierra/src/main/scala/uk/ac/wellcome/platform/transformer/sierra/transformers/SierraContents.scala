package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

object SierraContents extends SierraTransformer with MarcUtils {

  type Output = Option[String]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    getFirstSubfieldContent(bibData, "505", "a")
}
