package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

trait SierraPartName extends MarcUtils {

  def getPartName(bibData: SierraBibData): Option[String] =
    getMatchingVarFields(bibData, "245").flatMap {
      getSubfieldContents(_, Some("p"))
    }.headOption
}
