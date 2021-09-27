package weco.pipeline.transformer.calm.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.catalogue.internal_model.languages.{Language, MarcLanguageCodeList}

import scala.util.matching.Regex

object CalmLanguages {

  // Parses the "Language" field on a Calm record.
  //
  // Returns a list of Languages on a Work, and an optional LanguageNote.
  //
  // If the field can be completely parsed as a list of languages,
  // then we discard the original field, e.g. "English, German and French".
  //
  // If the field contains a more in-depth description, then we extract any
  // languages we can, and keep the original sentence in a note.
  // e.g. "Mainly in German, smaller parts in English."
  //
  def apply(languageFieldValues: List[String]): (List[Language], List[Note]) =
    languageFieldValues
      .foldLeft((List[Language](), List[Note]())) {
        case ((languages, notes), value) =>
          val (newLanguages, newNotes) = parseSingleValue(value)
          ((languages ++ newLanguages).distinct, (notes ++ newNotes).distinct)
      }

  private def parseSingleValue(
    languageField: String): (List[Language], List[Note]) =
    languageField match {
      case value if value.trim.nonEmpty =>
        parseLanguages(value) match {
          case Some(languages) => (languages, List())

          // If we're unable to parse the whole string as a list of languages,
          // then guess at what languages it contains, then copy the field
          // verbatim to a LanguageNote.  This means the Language list will
          // contain structured data, and we also keep the complete text from
          // the catalogue.
          case None =>
            (
              guessLanguages(value),
              List(
                Note(
                  contents = value.replace("recieved", "received"),
                  noteType = NoteType.LanguageNote
                )
              )
            )
        }

      case _ => (List.empty, List())
    }

  private def parseLanguages(langField: String): Option[List[Language]] =
    langField match {
      case ExactLanguageMatch(languages) => Some(languages)
      case MultiLanguageMatch(languages) => Some(languages)
      case FuzzyLanguageMatch(languages) => Some(languages)
      case LanguageTagMatch(languages)   => Some(languages)
      case _                             => None
    }

  // If the contents of the field exactly matches the name of a
  // language in the MARC Language Code list, then use that.
  private object ExactLanguageMatch {
    def unapply(langField: String): Option[List[Language]] =
      MarcLanguageCodeList
        .fromName(langField)
        .map { List(_) }
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
      // the boundary regex -- it only matches "and" if it's not
      // part of a longer word.
      val components = langField
        .multisplit("\n", ";", "\\.", ",", "/", "\\band\\b", "`")
        .map { _.trim }
        .filter { _.nonEmpty }

      val matchedLanguages = components.flatMap {
        MarcLanguageCodeList.fromName
      }.toList

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
          s.split(separators.head).flatMap { _.multisplit(separators.tail: _*) }
        }
    }
  }

  // Some of our CALM records have <language> tags, of the form:
  //
  //    <language langcode="ger">German, </language>
  //    <language>French</language>
  //
  // Throw away the <language> tags, then re-run them through the parser.
  // We discard values of the langcode attribute.  In theory we could
  // check them for consistency, but I haven't seen any cases where they
  // don't match and it increases the complexity.
  private object LanguageTagMatch {
    val pattern = new Regex(
      "<language(?: langcode=\"[a-z]+\")?>([^<]+)</language>",
      "label"
    )

    def unapply(langField: String): Option[List[Language]] = {
      val taglessLangField =
        pattern.replaceAllIn(langField, (m: Regex.Match) => m.group("label"))

      if (langField != taglessLangField) {
        parseLanguages(taglessLangField)
      } else {
        None
      }
    }
  }

  // This has some rules tuned to our Calm data, with fixes for certain
  // records that are close to matches, but need a bit of fixing up.
  // e.g. typos, spelling errors, different hyphenation to the MARC list.
  //
  // This list is deliberately conservative; the intent is to handle minor
  // errors rather than editorialise the list.  Any fixes added here should
  // pass the sniff test "Yes, the cataloguer obviously meant X".
  //
  // Note: we should flag these issues and fix them in the source where
  // appropriate.
  private object FuzzyLanguageMatch {
    def unapply(langField: String): Option[List[Language]] = {
      val correctedLangField =
        langField
          .replace("Portugese", "Portuguese")
          .replace("Portguese", "Portuguese")
          .replace("Potuguese", "Portuguese")
          .replace("Swiss-German", "Swiss German")
          .replace("Norweigan", "Norwegian")
          .replace("Lugandan", "Luganda")
          .replaceAll("^Eng$", "English")
          .replaceAll("^Language$", "") // We can't do anything useful with this!

      if (langField != correctedLangField) {
        parseLanguages(correctedLangField)
      } else {
        None
      }
    }
  }

  // Make a best effort guess of the languages in a particular string.
  //
  // We rely on the MARC Language list to tell us if a given capitalised word
  // is actually a language, and then a language won't appear if it's not used.
  // e.g. nobody will write "This doesn't contain English"
  //
  // Any capitalised word could be a language, but we can't assume it is,
  // e.g. the word "Mostly" or "Partly" could appear in this field, but that's
  // not a language name!
  val languageNamePattern: Regex = "[A-Z][a-z]+".r

  private def guessLanguages(langField: String): List[Language] =
    languageNamePattern
      .findAllIn(langField)
      .flatMap { MarcLanguageCodeList.fromName }
      .toList
}
