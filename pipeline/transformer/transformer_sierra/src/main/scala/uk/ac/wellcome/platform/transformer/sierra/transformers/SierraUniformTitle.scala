package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

trait SierraUniformTitle extends MarcUtils {

  def getUniformTitle(bibData: SierraBibData): Option[String] =
    getMatchingVarFields(bibData, "240").flatMap {
      getSubfieldContents(_, Some("a"))
    }.headOption
}
