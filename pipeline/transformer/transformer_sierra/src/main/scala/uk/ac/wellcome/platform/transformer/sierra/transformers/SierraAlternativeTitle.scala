package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

trait SierraAlternativeTitle extends MarcUtils {

  def getAlternativeTitle(bibData: SierraBibData): Option[String] =
    getMatchingVarFields(bibData, "246").flatMap {
      getSubfieldContents(_, Some("a"))
    }.headOption
}
