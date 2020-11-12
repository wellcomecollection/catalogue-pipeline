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
  type Output = Seq[Language]

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
  override def apply(bibData: SierraBibData): Seq[Language] = {
    val primaryLanguage =
      bibData.lang
        .map { lang =>
          (
            lang.code,
            MarcLanguageCodeList.lookupByCode(lang.code).getOrElse(lang.name))
        }
        .map { case (code, label) => Language(id = code, label = label) }

    val additionalLanguages =
      bibData
        .subfieldsWithTag("041" -> "a")
        .contents
        .map { code =>
          (code, MarcLanguageCodeList.lookupByCode(code))
        }
        .map {
          case (code, Some(label)) => Some(Language(id = code, label = label))
          case (code, None) =>
            warn(s"Unrecognised code in MARC 041 ǂa: $code")
            None
        }

    (Seq(primaryLanguage) ++ additionalLanguages).flatten.distinct
  }
}
