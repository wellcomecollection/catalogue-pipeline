package uk.ac.wellcome.models.marc

import grizzled.slf4j.Logging

import scala.xml.XML

// Utilities for parsing the MARC Code List for Languages to looking up
// language labels.
//
// This parses the XML file downloaded from:
// https://www.loc.gov/standards/codelists/languages.xml
//
object MarcLanguageCodeList extends Logging {

  // Create a lookup from code -> name
  private val codeLookup: Map[String, String] = {
    val languages =
      XML.load(getClass.getResourceAsStream("/languages.xml")) \\ "language"

    val codeNamePairs = languages
      .map { lang =>
        val code = (lang \ "code").text
        val name = (lang \ "name").text

        code -> name
      }

    // This checks that we aren't repeating codes.  This should be handled
    // by languages.xml, but check we're parsing it correctly.
    assert(codeNamePairs.size == codeNamePairs.toMap.size)

    codeNamePairs.toMap
  }

  // Create a lookup from name -> code
  //
  // Note: languages.xml includes variant names.  I haven't implemented
  // support for these names in this lookup, because I can't see any
  // examples of them in Calm (where we need to parse names).  If we need
  // to parse these variant names, that data is available.
  //
  // Note: We can't simply invert the (code -> name) map.  The MARC
  // Language Code list includes some obsoleted codes, for example:
  //
  //    <language>
  //      <name authorized="yes">Tagalog</name>
  //      <code>tgl</code>
  //      …
  //    </language>
  //    <language>
  //      <name>Tagalog</name>
  //      <code status="obsolete">tag</code>
  //      ...
  //    </language>
  //
  // We want to be able to resolve those codes to a name, but we want to
  // choose the non-obsolete code when resolving the name.
  //
  private val nameLookup: Map[String, String] = {
    val languages =
      XML.load(getClass.getResourceAsStream("/languages.xml")) \\ "language"

    val nameCodePairs = languages
      .map { lang =>
        val code = lang \ "code"
        val name = lang \ "name"

        name -> code
      }
      .filterNot {
        case (_, code) =>
          val status = code.head.attribute("status").map { _.toString }
          status.contains("obsolete")
      }
      .map {
        case (name, code) => name.text -> code.text
      }

    // This checks that we aren't repeating names.  This should be handled
    // by languages.xml, but check we're parsing it correctly.
    assert(nameCodePairs.size == nameCodePairs.toMap.size)

    nameCodePairs.toMap
  }

  // Returns the name of a language with the given code, if any
  def lookupByCode(code: String): Option[String] = {
    if (code.length != 3) {
      warn(
        s"MARC language codes are 3 letters long; got $code (length ${code.length})")
    }
    codeLookup.get(code)
  }

  // Returns the code of a language with the given name, if any
  def lookupByName(name: String): Option[String] =
    nameLookup.get(name)
}
