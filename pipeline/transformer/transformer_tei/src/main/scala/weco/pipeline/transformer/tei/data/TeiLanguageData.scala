package weco.pipeline.transformer.tei.data

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.languages.{Language, MarcLanguageCodeList}

/** The TEI language data uses the IANA language codes from:
  * https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
  *
  * In the rest of the pipeline, we use MARC language codes.  We need to be consistent
  * so we can filter/aggregate languages across sources.
  *
  * This object maps languages from the TEI files into MARC-based language codes.
  * Trying to create a complete IANA-to-MARC mapper is beyond the scope of the pipeline,
  * and unnecessary -- our TEI files only use a small subset of IANA languages.
  *
  */
object TeiLanguageData extends Logging {

  /** Given a <textLang> element of the form
    *
    *     <textLang mainLang={id}>{label}</textLang>
    *
    * or
    *
    *     <textLang otherLangs={id}>{label}</textLang>
    *
    * Create a Language based on the MARC language code list.
    */
  def apply(id: String, label: Option[String]): Option[Language] =
    (id, label) match {

      case ("", None) => None
      case ("", Some(s)) if s.trim.isEmpty => None

      // Map languages where there's a 1:1 correspondence between the IANA language
      // and the MARC language codes.
      //
      // Note that we match based on both the code *and* the name -- this is to
      // help spot problems that should be fixed in the source data,
      // e.g. mismatched code/label or the wrong code for a language.
      //
      case ("ar", Some("Arabic"))             => MarcLanguageCodeList.fromName("Arabic")
      case ("sa", Some("Sanskrit"))           => MarcLanguageCodeList.fromName("Sanskrit")
      case ("he", Some("Hebrew"))             => MarcLanguageCodeList.fromName("Hebrew")
      case ("ms", Some("Malay"))              => MarcLanguageCodeList.fromName("Malay")
      case ("eng", Some("English"))           => MarcLanguageCodeList.fromName("English")
      case ("en", Some("English"))            => MarcLanguageCodeList.fromName("English")
      case ("hi", Some("Hindi"))              => MarcLanguageCodeList.fromName("Hindi")
      case ("jv", Some("Javanese"))           => MarcLanguageCodeList.fromName("Javanese")
      case ("pra", Some("Prakrit languages")) => MarcLanguageCodeList.fromName("Prakrit languages")
      case ("it", Some("Italian"))            => MarcLanguageCodeList.fromName("Italian")
      case ("ta", Some("Tamil"))              => MarcLanguageCodeList.fromName("Tamil")
      case ("jpr", Some("Judeo-Persian"))     => MarcLanguageCodeList.fromName("Judeo-Persian")
      case ("la", Some("Latin"))              => MarcLanguageCodeList.fromName("Latin")
      case ("la", Some("Latin"))              => MarcLanguageCodeList.fromName("Latin")
      case ("cop", Some("Coptic"))            => MarcLanguageCodeList.fromName("Coptic")
      case ("es", Some("Spanish"))            => MarcLanguageCodeList.fromName("Spanish")
      case ("btk", Some("Batak"))             => MarcLanguageCodeList.fromName("Batak")
      case ("fa", Some("Persian"))            => MarcLanguageCodeList.fromName("Persian")
      case ("ji", Some("Yiddish"))            => MarcLanguageCodeList.fromName("Yiddish")
      case ("yi", Some("Yiddish"))            => MarcLanguageCodeList.fromName("Yiddish")
      case ("fr", Some("French"))             => MarcLanguageCodeList.fromName("French")

      // The IANA entry for "grc" is "Ancient Greek (to 1453)"
      case ("grc", Some("Ancient Greek")) => MarcLanguageCodeList.fromName("Greek, Ancient (to 1453)")
      case ("grc", Some("Greek"))         => MarcLanguageCodeList.fromName("Greek, Ancient (to 1453)")

      // The IANA entry for "el" is "Modern Greek (1453-)"
      case ("el", Some("Greek")) => MarcLanguageCodeList.fromName("Greek, Modern (1453- )")

      // The IANA entry for "egy" is "Egyptian (Ancient)".  There doesn't seem to be
      // a distinction for this in MARC.
      case ("egy", Some("Ancient Egyptian"))   => MarcLanguageCodeList.fromName("Egyptian")
      case ("egy", Some("Egyptian (Ancient)")) => MarcLanguageCodeList.fromName("Egyptian")

      // The IANA entry for "spq" is "Loreto-Ucayali Spanish".  For now we file it under
      // "Spanish", but we should ask the TEI team if they want to use the longer form.
      case ("spq", Some("Spanish")) => MarcLanguageCodeList.fromName("Spanish")

      // This is a weird one that might want fixing in the TEI data.
      case ("es-ES", Some("Spanish Spain")) => MarcLanguageCodeList.fromName("Spanish")

      // Map languages where there isn't a 1:1 distinction, or where the IANA language
      // is an alternative name for one of the MARC languages.  We use the MARC code, but
      // the TEI label.
      //
      // This means we'll display the most accurate label on the individual work pages,
      // but these works will filter/aggregate alongside the "parent" language.
      //
      case ("btx", Some("Karo-Batak"))           => Some(Language(id = "btk", label = "Karo-Batak"))
      case ("bbc", Some("Toba-Batak"))           => Some(Language(id = "btk", label = "Toba-Batak"))
      case ("btk", Some("Toba-Batak"))           => Some(Language(id = "btk", label = "Toba-Batak"))
      case ("gu", Some("(Old) Gujarati"))        => Some(Language(id = "guj", label = "(Old) Gujarati"))
      case ("btd", Some("Batak Dairi"))          => Some(Language(id = "btd", label = "Batak Dairi"))
      case ("ms", Some("Middle Malay"))          => Some(Language(id = "may", label = "Middle Malay"))
      case ("pka", Some("Ardhamāgadhi Prakrit")) => Some(Language(id = "pra", label = "Ardhamāgadhi Prakrit"))
      case ("pka", Some("Ardhamāgadhī Prākrit")) => Some(Language(id = "pra", label = "Ardhamāgadhī Prākrit"))
      case ("itk", Some("Judeo-Italian"))         => Some(Language(id = "ita", label = "Judeo-Italian"))
      case ("jv", Some("Java"))                   => Some(Language(id = "jav", label = "Java"))

      // If we're not sure what to do, don't map any language for now.  Drop a warning in
      // the logs for us to come back and investigate further.
      case (id, label) =>
        warn(s"Unable to map TEI language to catalogue language: id=$id, label=$label")
        None
    }
}
