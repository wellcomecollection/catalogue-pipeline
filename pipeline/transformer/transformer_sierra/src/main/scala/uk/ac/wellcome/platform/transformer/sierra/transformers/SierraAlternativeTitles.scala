package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData
import uk.ac.wellcome.platform.transformer.sierra.source.VarField

trait SierraAlternativeTitles extends MarcUtils {

  def getAlternativeTitles(bibData: SierraBibData): List[String] =
    getAlternativeTitleFields(bibData).flatMap {
      getSubfieldContents(_, Some("a"))
    }

  def getAlternativeTitleFields(bibData: SierraBibData): List[VarField] =
    getMatchingVarFields(bibData, "240") ++
      getMatchingVarFields(bibData, "130") ++
      getMatchingVarFields(bibData, "246").filterNot {
        _.indicator2.contains("6")
      }
}
