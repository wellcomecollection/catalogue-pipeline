package uk.ac.wellcome.models.marc

import scala.xml.XML

// Utilities for parsing the MARC Code List for Languages to looking up
// language labels.
//
// This parses the XML file downloaded from:
// https://www.loc.gov/standards/codelists/languages.xml
//
object MarcLanguageCodeList {
  private val idLookup: Map[String, String] = {
    val languages =
      XML.load(getClass.getResourceAsStream("/languages.xml")) \\ "language"

    val idNamePairs = languages
      .map { lang =>
        val code = (lang \ "code").text
        val name = (lang \ "name").text

        code -> name
      }

    // This checks that we aren't repeating codes.  This should be handled
    // by languages.xml, but check we're parsing it correctly.
    assert(idNamePairs.size == idNamePairs.toMap.size)

    idNamePairs.toMap
  }

  def lookupById(code: String): Option[String] =
    idLookup.get(code)
}
