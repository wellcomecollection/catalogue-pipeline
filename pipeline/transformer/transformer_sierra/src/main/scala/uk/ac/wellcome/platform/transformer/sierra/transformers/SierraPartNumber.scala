package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData

trait SierraPartNumber extends MarcUtils {

  def getPartNumber(bibData: SierraBibData): Option[String] =
    getMatchingVarFields(bibData, "245").flatMap {
      getSubfieldContents(_, Some("n"))
    }.headOption
}
