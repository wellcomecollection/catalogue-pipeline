package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

trait SierraEdition extends MarcUtils {

  def getEdition(bibData: SierraBibData): Option[String] =
    getMatchingVarFields(bibData, "250").flatMap {
      getSubfieldContents(_, Some("a"))
    }.headOption
}
