package weco.pipeline.transformer.marc_common.transformers

import weco.pipeline.transformer.marc_common.models.{
  MarcField,
  MarcRecord,
  MarcSubfield
}

// Populate work:alternativeTitles
//
// The following fields are used as possible alternative titles: (245 is the main title)
// * 240 $a https://www.loc.gov/marc/bibliographic/bd240.html (Uniform Title)
// * 130 $a http://www.loc.gov/marc/bibliographic/bd130.html (Main Entry - Uniform Title)
// * 246 $a https://www.loc.gov/marc/bibliographic/bd246.html (Varying Form of Title)
// * 242 $a https://www.loc.gov/marc/bibliographic/bd242.html (Translation of Title

object MarcAlternativeTitles extends MarcDataTransformer {

  override type Output = Seq[String]

  override def apply(record: MarcRecord): Seq[String] = {
    record
      .fieldsWithTags("240", "130", "246", "242")
      .withoutCaptionTitles
      .map(field => alternativeTitle(field))
      .filterNot(_.isEmpty)
      .distinct
  }

  private def alternativeTitle(field: MarcField): String =
    field.subfields.withoutUKLW.map(_.content).mkString(" ")

  implicit private class FieldsOps(fields: Seq[MarcField]) {

    // 246 with ind2 = 6 indicates a Caption Title
    // "printed at the head of the first page of text. Caption title: may be generated with the note for display."
    // This is not an alternative title that we want to capture here.
    def withoutCaptionTitles: Seq[MarcField] =
      fields filterNot {
        field =>
          field.marcTag == "246" && field.indicator2 == "6"
      }
  }
  implicit private class SubfieldsOps(subfields: Seq[MarcSubfield]) {
    // Any $5 subfield with contents `UkLW` is Wellcome Library-specific and
    // should be omitted.
    def withoutUKLW: Seq[MarcSubfield] =
      subfields.filterNot(_ == MarcSubfield("5", "UkLW"))

  }
}
