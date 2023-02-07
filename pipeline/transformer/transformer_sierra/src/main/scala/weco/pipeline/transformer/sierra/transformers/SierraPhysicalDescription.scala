package weco.pipeline.transformer.sierra.transformers

import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData

// Populate wwork:physicalDescription.
//
// We use MARC field 300 and subfields:
//
//    - ǂa = extent
//    - ǂb = other physical details
//    - ǂc = dimensions
//    - ǂe = accompanying material
//
// The underlying MARC values should have all the punctuation we need.
//
// Notes:
//
//  - MARC field 300 and subfield ǂb are both labelled "R" (repeatable).
//    This field does appear multiple times on some of our records, in particular
//    AV records.  See the tests for an example of this.
//
//  - Multiple physical descriptions go on multiple lines.
//
// https://www.loc.gov/marc/bibliographic/bd300.html
//
object SierraPhysicalDescription
    extends SierraDataTransformer
    with SierraQueryOps {

  type Output = Option[String]

  def apply(bibData: SierraBibData): Option[String] = {
    val lines =
      bibData
        .varfieldsWithTag("300")
        .flatMap {
          vf =>
            vf.subfieldsWithTags("a", "b", "c", "e").contentString(" ")
        }

    lines.mkString("<br/>") match {
      case s if s.isEmpty => None
      case s              => Some(s)
    }
  }
}
