package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData
import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber

object SierraEdition extends SierraTransformer with MarcUtils {

  type Output = Option[String]

  // Populate work:edition
  //
  // Field 250 is used for this. In the very rare case where multiple 250 fields
  // are found, they are concatenated into a single string
  def apply(bibId: SierraBibNumber, bibData: SierraBibData) = {
    val editions = getMatchingVarFields(bibData, "250").flatMap {
      getSubfieldContents(_, Some("a"))
    }
    editions match {
      case Nil      => None
      case editions => Some(editions.mkString(" "))
    }
  }
}
