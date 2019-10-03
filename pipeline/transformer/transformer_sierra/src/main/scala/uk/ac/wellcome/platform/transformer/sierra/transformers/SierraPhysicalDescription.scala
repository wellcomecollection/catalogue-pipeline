package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraQueryOps
}
import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber

// Populate wwork:physicalDescription.
//
// We use MARC field 300 and subfield $b.
//
// Notes:
//
//  - MARC field 300 and subfield $b are both labelled "R" (repeatable).
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
object SierraPhysicalDescription extends SierraTransformer with SierraQueryOps {

  type Output = Option[String]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    bibData
      .subfieldsWithTags(
        "300" -> "a",
        "300" -> "b",
        "300" -> "c",
        "563" -> "a"
      )
      .contentString("\n")
}
