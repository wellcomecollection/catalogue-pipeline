package weco.pipeline.transformer.tei.data

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.languages.{Language, MarcLanguageCodeList}

/** The TEI language data uses the IANA language codes from:
  * https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
  *
  * In the rest of the pipeline, we use MARC language codes. We need to be
  * consistent so we can filter/aggregate languages across sources.
  *
  * This object maps languages from the TEI files into MARC-based language
  * codes. Trying to create a complete IANA-to-MARC mapper is beyond the scope
  * of the pipeline, and unnecessary -- our TEI files only use a small subset of
  * IANA languages.
  */
object TeiLanguageData extends Logging {

  /** Given the ID and label from a <textLang> element of the form
    *
    * <textLang mainLang={id}>{label}</textLang>
    *
    * or
    *
    * <textLang otherLangs={id}>{label}</textLang>
    *
    * create a Language based on the MARC language code list.
    */
  def apply(id: String, label: String): Either[Throwable, Language] = {
    val result = (id, label) match {

      // Map languages where there's a 1:1 correspondence between the IANA language
      // and the MARC language codes.
      //
      // Note that we match based on both the code *and* the name -- this is to
      // help spot problems that should be fixed in the source data,
      // e.g. mismatched code/label or the wrong code for a language.
      //
      case ("ar", "Arabic")   => MarcLanguageCodeList.fromName("Arabic")
      case ("sa", "Sanskrit") => MarcLanguageCodeList.fromName("Sanskrit")
      case ("he", "Hebrew")   => MarcLanguageCodeList.fromName("Hebrew")
      case ("ms", "Malay")    => MarcLanguageCodeList.fromName("Malay")
      case ("eng", "English") => MarcLanguageCodeList.fromName("English")
      case ("en", "English")  => MarcLanguageCodeList.fromName("English")
      case ("hi", "Hindi")    => MarcLanguageCodeList.fromName("Hindi")
      case ("ja", "Japanese") => MarcLanguageCodeList.fromName("Japanese")
      case ("jv", "Javanese") => MarcLanguageCodeList.fromName("Javanese")
      case ("pra", "Prakrit languages") =>
        MarcLanguageCodeList.fromName("Prakrit languages")
      case ("it", "Italian") => MarcLanguageCodeList.fromName("Italian")
      case ("ta", "Tamil")   => MarcLanguageCodeList.fromName("Tamil")
      case ("jpr", "Judeo-Persian") =>
        MarcLanguageCodeList.fromName("Judeo-Persian")
      case ("la", "Latin")   => MarcLanguageCodeList.fromName("Latin")
      case ("cop", "Coptic") => MarcLanguageCodeList.fromName("Coptic")
      case ("es", "Spanish") => MarcLanguageCodeList.fromName("Spanish")
      case ("btk", "Batak")  => MarcLanguageCodeList.fromName("Batak")
      case ("fa", "Persian") => MarcLanguageCodeList.fromName("Persian")
      case ("ji", "Yiddish") => MarcLanguageCodeList.fromName("Yiddish")
      case ("yi", "Yiddish") => MarcLanguageCodeList.fromName("Yiddish")
      case ("fr", "French")  => MarcLanguageCodeList.fromName("French")

      // The IANA entry for "grc" is "Ancient Greek (to 1453)"
      case ("grc", "Ancient Greek") =>
        MarcLanguageCodeList.fromName("Greek, Ancient (to 1453)")
      case ("grc", "Greek") =>
        MarcLanguageCodeList.fromName("Greek, Ancient (to 1453)")

      // The IANA entry for "el" is "Modern Greek (1453-)"
      case ("el", "Greek") =>
        MarcLanguageCodeList.fromName("Greek, Modern (1453- )")

      // The IANA entry for "spq" is "Loreto-Ucayali Spanish".  For now we file it under
      // "Spanish", but we should ask the TEI team if they want to use the longer form.
      case ("spq", "Spanish") => MarcLanguageCodeList.fromName("Spanish")

      // This is a weird one that might want fixing in the TEI data.
      case ("es-es", "Spanish Spain") =>
        MarcLanguageCodeList.fromName("Spanish")

      // Map languages where there isn't a 1:1 distinction, or where the IANA language
      // is an alternative name for one of the MARC languages.  We use the MARC code, but
      // the TEI label.
      //
      // This means we'll display the most accurate label on the individual work pages,
      // but these works will filter/aggregate alongside the "parent" language.
      //
      case ("ar", "Arabic [Karshuni]") =>
        customLanguage("Arabic", overrideLabel = "Arabic [Karshuni]")
      case ("egy", "Ancient Egyptian") =>
        customLanguage("Egyptian", overrideLabel = "Ancient Egyptian")
      case ("egy", "Egyptian (Ancient)") =>
        customLanguage("Egyptian", overrideLabel = "Ancient Egyptian")
      case ("btx", "Karo-Batak") =>
        customLanguage("Batak", overrideLabel = "Karo-Batak")
      case ("bbc", "Toba-Batak") =>
        customLanguage("Batak", overrideLabel = "Toba-Batak")
      case ("btk", "Toba-Batak") =>
        customLanguage("Batak", overrideLabel = "Toba-Batak")
      case ("btd", "Batak Dairi") =>
        customLanguage("Batak", overrideLabel = "Batak Dairi")
      case ("gu", "(Old) Gujarati") =>
        customLanguage("Gujarati", overrideLabel = "(Old) Gujarati")
      case ("ms", "Middle Malay") =>
        customLanguage("Malay", overrideLabel = "Middle Malay")
      case ("pka", "Ardhamāgadhi Prakrit") =>
        customLanguage(
          "Prakrit languages",
          overrideLabel = "Ardhamāgadhi Prakrit"
        )
      case ("pka", "Ardhamāgadhī Prākrit") =>
        customLanguage(
          "Prakrit languages",
          overrideLabel = "Ardhamāgadhī Prākrit"
        )
      case ("itk", "Judeo-Italian") =>
        customLanguage("Italian", overrideLabel = "Judeo-Italian")
      case ("jv", "Java") => customLanguage("Javanese", overrideLabel = "Java")

      // If we're not sure what to do, don't map any language for now.  Drop a warning in
      // the logs for us to come back and investigate further.
      case (id, label) =>
        warn(
          s"Unable to map TEI language to catalogue language: id=$id, label=$label"
        )
        None
    }

    result match {
      case Some(lang) => Right(lang)
      case None =>
        Left(
          new Throwable(
            s"Unable to map TEI language to catalogue language: id=$id, label=$label"
          )
        )
    }
  }

  private def customLanguage(
    name: String,
    overrideLabel: String
  ): Option[Language] =
    MarcLanguageCodeList
      .fromName(name)
      .map {
        lang =>
          lang.copy(label = overrideLabel)
      }
}
