package weco.pipeline.transformer.sierra.transformers

import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.marc.Subfield

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
object SierraAlternativeTitles extends SierraDataTransformer with SierraQueryOps {

  type Output = List[String]

  def apply(bibData: SierraBibData): List[String] =
    bibData
      .varfieldsWithTags("240", "130", "246")
      .filterNot {
        varfield =>
          varfield.marcTag.contains("246") && varfield.indicator2.contains("6")
      }
      .flatMap {
        varfield =>
          varfield.subfields
            .filter {
              case Subfield("5", "UkLW") => false
              case _                     => true
            }
            .contentString(" ")
      }
      .distinct
}
