package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.source_model.sierra.SierraBibData
import weco.catalogue.source_model.sierra.source.SierraQueryOps

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
//    According to Branwen, this field does appear multiple times on some
//    of our records -- not usually on books, but on some of the moving image
//    & sound records.
//
//  - So far we don't do any stripping of punctuation, and if multiple
//    subfields are found on a record, I'm just joining them with newlines.
//
//    TODO: Decide a proper strategy for joining multiple physical
//    descriptions!
//
// https://www.loc.gov/marc/bibliographic/bd300.html
//
object SierraPhysicalDescription
    extends SierraDataTransformer
    with SierraQueryOps {

  type Output = Option[String]

  def apply(bibData: SierraBibData) =
    bibData
      .subfieldsWithTags(
        "300" -> "a",
        "300" -> "b",
        "300" -> "c",
        "300" -> "e",
      )
      .contentString(" ")
}
