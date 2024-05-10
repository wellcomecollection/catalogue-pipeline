package weco.pipeline.transformer.marc_common.transformers

import weco.catalogue.internal_model.languages.{Language, MarcLanguageCodeList}
import weco.pipeline.transformer.marc_common.models.MarcRecord

/*
 * Extracts the language from control field 008 of a MARC record
 *
 * Field 008 is a fixed-length data element that provides information
 * about the record as a whole. The language code is located at positions 35-37.
 *
 * See: https://www.loc.gov/marc/bibliographic/bd008a.html
 */
object MarcLanguage extends MarcDataTransformer {
  type Output = Option[Language]

  override def apply(record: MarcRecord): Option[Language] = {
    record.controlField("008").flatMap {
      controlField =>
        val fieldLength = controlField.content.length
        // Assume missing material specific coded elements between 18-34,
        // it seems common for this field to be omit positions in this range
        // so read from end of field backwards.
        MarcLanguageCodeList.fromCode(code =
          controlField.content.slice(
            from = fieldLength - 5,
            until = fieldLength - 2
          )
        )
    }
  }
}
