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
      case ExactLanguageMatch(languages)       => (languages, None)
      case MultiLanguageMatch(languages)       => (languages, None)
      case FuzzyLanguageMatch(languages, note) => (languages, note)
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

      // These are the components that might separate languages in
      // a string.  See the test cases for examples from Calm data.
      //
      // Note: we need to be a little careful splitting on "and",
      // because some languages have "and" in the name.  The \b is
      // the boundary regex.
      val components = langField
        .multisplit("\n", ";", "\\.", ",", "/", "\\band\\b", "`")
        .map { _.trim }

      val matchedLanguages = components
        .map { name => name -> MarcLanguageCodeList.lookupByName(name) }
        .collect { case (label, Some(code)) => Language(label = label, id = code) }
        .toList

      // If there were some unmatched components, this isn't right --
      // return nothing.
      if (matchedLanguages.size == components.size) {
        Some(matchedLanguages)
      } else {
        assert(matchedLanguages.size < components.size)
        None
      }
    }

    implicit class StringOps(s: String) {

      // Split a string on multiple separators.  Example:
      //
      //     > "123 and 456 or 789".multisplit("and", "or")
      ///    Seq("123 ", " 456 ", " 789")
      //
      def multisplit(separators: String*): Seq[String] =
        if (separators.isEmpty) {
          Seq(s)
        } else {
          s.split(separators.head).flatMap { _.multisplit(separators.tail: _*)}
        }
    }
  }

  // This has some rules tuned to our Calm data, with fixes for certain
  // records that are close to matches, but need a bit of fixing up.
  // e.g. typos, spelling errors, different hyphenation to the MARC list.
  //
  // Note: we should flag these issues and fix them in the source where
  // appropriate.
  private object FuzzyLanguageMatch {
    def unapply(langField: String): Option[(List[Language], Option[LanguageNote])] = {
      val correctedLangField =
        langField
          .replace("Portugese", "Portuguese")
          .replace("Portguese", "Portuguese")
          .replace("Swiss-German", "Swiss German")
          .replace("Norweigan", "Norwegian")

      if (langField != correctedLangField) {
        parseField(correctedLangField) match {
          case (Nil, None) => None
          case other       => Some(other)
        }
      } else {
        None
      }
    }
  }
}
