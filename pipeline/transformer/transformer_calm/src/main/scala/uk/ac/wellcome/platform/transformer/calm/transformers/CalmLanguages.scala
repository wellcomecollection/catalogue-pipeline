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
      case Some(langField) if langField.trim.nonEmpty => parseField(langField)
      case _                                          => (List.empty, None)
    }

  private def parseField(langField: String): (List[Language], Option[LanguageNote]) =
    langField match {
      case ExactLanguageMatch(languages) => (languages, None)
      case MultiLanguageMatch(languages) => (languages, None)
      case _ => (List.empty, None)
    }

  // If the contents of the field exactly matches the name of a
  // language in the MARC Language Code list, then use that.
  private object ExactLanguageMatch {
    def unapply(langField: String): Option[List[Language]] =
      MarcLanguageCodeList
        .lookupByName(langField)
        .map { langCode =>
          List(Language(id = langCode, label = langField))
        }
  }

  // If the contents of the field are a collection of matches for
  // languages in the MARC Language Code list, then use them.
  private object MultiLanguageMatch {
    def unapply(langField: String): Option[List[Language]] = {
      val components = langField
        .multisplit("\n", ";", "\\.")
        .map { _.trim }

      val matchedLanguages = components
        .map { name => name -> MarcLanguageCodeList.lookupByName(name) }
        .collect { case (label, Some(code)) => Language(label = label, id = code) }
        .toList

      if (matchedLanguages.size == components.size) {
        Some(matchedLanguages)
      } else {
        assert(matchedLanguages.size < components.size)
        None
      }
    }

    implicit class StringOps(s: String) {
      def multisplit(separators: String*): Seq[String] =
        if (separators.isEmpty) {
          Seq(s)
        } else {
          s.split(separators.head).flatMap { _.multisplit(separators.tail: _*)}
        }
    }
  }
}
