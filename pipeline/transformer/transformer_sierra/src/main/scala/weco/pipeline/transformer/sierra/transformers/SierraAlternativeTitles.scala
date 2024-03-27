package weco.pipeline.transformer.sierra.transformers

import weco.pipeline.transformer.marc_common.transformers.MarcAlternativeTitles
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.models.data.SierraBibData

// Populate work:alternativeTitles
//
// The following fields are used as possible alternative titles:
// * 240 $a https://www.loc.gov/marc/bibliographic/bd240.html
// * 130 $a http://www.loc.gov/marc/bibliographic/bd130.html
// * 246 $a https://www.loc.gov/marc/bibliographic/bd246.html
//
// 246 is only used when indicator2 is not equal to 6, as this is used for
// the work:lettering field
//
// Any $5 subfield with contents `UkLW` is Wellcome Library-specific and
// should be omitted.
object SierraAlternativeTitles
    extends SierraDataTransformer
    with SierraMarcDataConversions {

  type Output = List[String]

  def apply(bibData: SierraBibData): List[String] =
    MarcAlternativeTitles(bibData).toList
}
