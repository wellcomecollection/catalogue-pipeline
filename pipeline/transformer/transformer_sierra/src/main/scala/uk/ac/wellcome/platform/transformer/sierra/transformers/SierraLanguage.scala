package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal.Language
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

object SierraLanguage extends SierraTransformer {

  type Output = Option[Language]

  // Populate wwork:language.
  //
  // We use the "lang" field, if present.
  //
  // Notes:
  //
  //  - "lang" is an optional field in the Sierra API.
  //  - These are populated by ISO 639-2 language codes, but we treat them
  //    as opaque identifiers for our purposes.
  //
  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    bibData.lang.map { lang =>
      Language(
        label = lang.name,
        id = Some(lang.code)
      )
    }
}
