package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraQueryOps,
}
import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber

// Populate work:alternativeTitles
//
// The following fields are used as possible alternative titles:
// * 240 $a https://www.loc.gov/marc/bibliographic/bd240.html
// * 130 $a http://www.loc.gov/marc/bibliographic/bd130.html
// * 246 $a https://www.loc.gov/marc/bibliographic/bd246.html
//
// 246 is only used when indicator2 is not equal to 6, as this is used for
// the work:lettering field
object SierraAlternativeTitles extends SierraTransformer with SierraQueryOps {

  type Output = List[String]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    bibData
      .varfieldsWithTags("240", "130", "246")
      .filterNot { varfield =>
        varfield.marcTag.contains("246") && varfield.indicator2.contains("6")
      }
      .subfieldsWithTag("a")
      .contents
}
