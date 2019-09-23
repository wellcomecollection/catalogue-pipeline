package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  VarField
}
import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber

object SierraAlternativeTitles extends SierraTransformer with MarcUtils {

  type Output = List[String]

  // Populate work:alternativeTitles
  //
  // The following fields are used as possible alternative titles:
  // * 240 $a https://www.loc.gov/marc/bibliographic/bd240.html
  // * 130 $a http://www.loc.gov/marc/bibliographic/bd130.html
  // * 246 $a https://www.loc.gov/marc/bibliographic/bd246.html
  //
  // 246 is only used when indicator2 is not equal to 6, as this is used for
  // the work:lettering field
  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
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
