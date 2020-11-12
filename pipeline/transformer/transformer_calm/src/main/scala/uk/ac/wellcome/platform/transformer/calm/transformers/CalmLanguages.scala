package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.models.marc.MarcLanguageCodeList
import uk.ac.wellcome.models.work.internal.{Language, LanguageNote}

object CalmLanguages {

  // Parses the "Language" field on a Calm record.
  //
  // Returns a list of Languages for the languages field on a Work,
  // and an optional LanguageNote if the original data cannot be
  // fully parsed as a series of languages.
  def apply(languagesField: Option[String]): (List[Language], Option[LanguageNote]) =
    languagesField match {
      case Some(langField) if langField.nonEmpty => parseField(langField)
      case _                                     => (List.empty, None)
    }

  private def parseField(langField: String): (List[Language], Option[LanguageNote]) =
    MarcLanguageCodeList.lookupByName(langField) match {
      // If the contents of the field exactly matches the name of a
      // language in the MARC Language Code list, then use that and finish.
      case Some(langCode) =>
        (List(Language(id = langCode, label = langField)), None)

      case _ => (List.empty, None)
    }
}
