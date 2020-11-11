package uk.ac.wellcome.models.marc

import scala.xml.XML

// Utilities for parsing the MARC Code List for Languages to looking up
// language labels.
//
// This parses the XML file downloaded from:
// https://www.loc.gov/standards/codelists/languages.xml
//
object MarcLanguageMap {
  private val idLookup: Map[String, String] = {
    val languages =
      XML.load(getClass.getResourceAsStream("/languages.xml")) \\ "language"

    languages
      .map { lang =>
        val code = (lang \ "code").text
        val name = (lang \ "name").text

        code -> name
      }
      .toMap
  }

  def lookupById(code: String): Option[String] =
    idLookup.get(code)
}
