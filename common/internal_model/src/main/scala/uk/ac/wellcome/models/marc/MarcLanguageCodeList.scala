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
  private lazy val codeLookup: Map[String, String] = {
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
  // Note: the same name might resolve to multiple codes if it is a
  // variant name of a language, e.g. Inuit.  I've checked, and there are
  // no cases where a variant name is *also* the primary name of a language
  // code.  In these cases, we pick the first one -- in practice, I'm not
  // sure our data will ever encounter this issue.
  //
  private lazy val nameLookup: Map[String, Seq[String]] = {
    val languages =
      XML.load(getClass.getResourceAsStream("/languages.xml")) \\ "language"

    languages
      .flatMap { lang =>
        val code = lang \ "code"

        // We go down into all instances of <name> to be sure we're
        // catching variant names, e.g. Chinese/Mandarin
        (lang \\ "name").map { _ -> code }
      }
      .filterNot {
        case (_, code) =>
          val status = code.head.attribute("status").map { _.toString }
          status.contains("obsolete")
      }
      .map {
        case (name, code) => name.text -> code.text
      }
      .groupBy { case (name, _) => name }
      .map {
        case (name, pairs) =>
          name -> pairs.collect { case (_, code) => code }
      }
  }

  // Returns the name of the language with the given code, if any
  def lookupNameForCode(code: String): Option[String] = {
    if (code.length != 3) {
      warn(
        s"MARC language codes are 3 letters long; got $code (length ${code.length})")
    }

    codeLookup.get(code)
  }

  // Returns the language code for the given name, if any
  def lookupCodeForName(name: String): Option[String] =
    nameLookup.get(name) match {
      case Some(Seq(code)) => Some(code)
      case Some(codes) =>
        warn(s"Multiple language codes for name $name: $codes")
        Some(codes.head)
      case _ => None
    }
}
