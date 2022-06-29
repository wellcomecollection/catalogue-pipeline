package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.Elem

/**
  * Extract a provenance[1] element as a Note, including the att.datable.w3c[2] attributes
  * expressed as prose in the Note text.
  * 1. https://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-provenance.html,
  * 2. https://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-att.datable.w3c.html
  */
object TeiProvenanceNote {
  def apply(provenance: Elem): Option[Note] =
    NormaliseText(provenance.text.trim).map(
      provenanceText =>
        Note(
          NoteType.OwnershipNote,
          List(formatProvenancePrefix(provenance), Some(provenanceText)).flatten
            .mkString(" ")
      ))

  private def formatProvenancePrefix(provenance: Elem): Option[String] = {
    // The order of this list is reflected in the output, and is intended to give an
    // appropriate narrative order.
    // In reality, "when" would not be used with the other attributes,
    // and at most one from each of the pairs "from/notBefore" and "to/notAfter",
    // is expected.
    // If more attributes are supplied than expected, this will be represented
    // faithfully in the output, rather than deciding which attributes take priority.
    List(
      ("when", ""),
      ("from", "from"),
      ("notBefore", "not before"),
      ("to", "to"),
      ("notAfter", "not after"),
    ).flatMap {
      case (attribute, label) => attributeAsText(provenance, attribute, label)
    } match {
      case Nil        => None
      case timeBounds => Some(s"(${timeBounds.mkString(", ")}):")
    }
  }

  private def attributeAsText(provenance: Elem,
                              attribute: String,
                              label: String): Option[String] =
    (label, provenance \@ attribute) match {
      case (_, "")        => None
      case ("", value)    => Some(value)
      case (label, value) => Some(List(label, value).mkString(" "))
    }
}
