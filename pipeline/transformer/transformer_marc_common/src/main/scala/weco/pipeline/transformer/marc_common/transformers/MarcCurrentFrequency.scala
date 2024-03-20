package weco.pipeline.transformer.marc_common.transformers
import weco.pipeline.transformer.marc_common.models.{MarcRecord, MarcSubfield}

// We use MARC field "310".  We join ǂa and ǂb with a space.
//
// Notes:
//  - Although 310 is theoretically repeatable, in practice we use it only once
//    on all but a handful of records.  In those cases, join with a space.
//  - As of November 2022, we only use 310 subfields ǂa and ǂb.
//  - As of March 2024, I have only seen 310ǂa in EBSCO sample data, and only one 310 per record.
//
// See https://www.loc.gov/marc/bibliographic/bd310.html
//

object MarcCurrentFrequency extends MarcDataTransformer {

  override type Output = Option[String]

  override def apply(record: MarcRecord): Option[String] = {
    Option(
      record
        .fieldsWithTags("310")
        .map(
          field =>
            field.subfields
              .collect {
                case MarcSubfield(tag, content)
                    if Seq("a", "b").contains(tag) =>
                  content
              }
              .mkString(" ")
        )
        .map(_.trim)
        .mkString(" ")
    ).filter(_.trim.nonEmpty)
  }
}
