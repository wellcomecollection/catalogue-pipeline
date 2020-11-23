package uk.ac.wellcome.platform.transformer.sierra.transformers

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.marc.MarcLanguageCodeList
import uk.ac.wellcome.models.work.internal.Language
import uk.ac.wellcome.platform.transformer.sierra.source.{
  SierraBibData,
  SierraQueryOps
}

object SierraLanguages
    extends SierraDataTransformer
    with SierraQueryOps
    with Logging {
  type Output = List[Language]

  // We only want to display languages that actually correspond to languages.
  // These language codes don't tell us anything useful, so we suppress them.
  private val suppressedLanguageCodes: Set[String] = Set(
    "mul",  // Multiple languages
    "und",  // Undetermined
    "zxx",  // No linguistic content
  )

  // Populate wwork:language.
  //
  // Rules:
  //
  //  - The first language comes from 008/35-37.  The Sierra API provides
  //    this via the "lang" field.
  //    See https://www.loc.gov/marc/bibliographic/bd008.html
  //
  //  - Subsequent languages come from MARC 041 ǂa.
  //    This is a repeatable field, as is the subfield.
  //    See https://www.loc.gov/marc/bibliographic/bd041.html
  //
  override def apply(bibData: SierraBibData): List[Language] = {
    val primaryLanguage =
      bibData.lang
        .map { lang =>
          MarcLanguageCodeList
            .lookupByCode(lang.code)
            .getOrElse(Language(label = lang.name, id = lang.code))
        }

    val additionalLanguages =
      bibData
        .subfieldsWithTag("041" -> "a")
        .contents
        .map { code =>
          (code, MarcLanguageCodeList.lookupByCode(code))
        }
        .map {
          case (_, Some(lang)) => Some(lang)
          case (code, None) =>
            warn(s"Unrecognised code in MARC 041 ǂa: $code")
            None
        }

    (List(primaryLanguage) ++ additionalLanguages)
      .flatten
      .filterNot { lang => suppressedLanguageCodes.contains(lang.id) }
      .distinct
  }
}
