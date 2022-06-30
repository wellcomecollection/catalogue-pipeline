package weco.pipeline.transformer.tei.transformers

import scala.xml.Elem

/**
 * Implementation of the datable.w3c attributes that can be present on certain TEI Elements
 * https://www.tei-c.org/release/doc/tei-p5-doc/en/html/ref-att.datable.w3c.html
 */
trait Datable {

  /**
   * Convert the datable.w3c attributes on `datableElement` into prose text
   */
  def formatDatablePrefix(datableElement: Elem): Option[String] = {
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
      case (attributeName, proseLabel) =>
        attributeAsText(datableElement, attributeName, proseLabel)
    } match {
      case Nil        => None
      case timeBounds => Some(s"(${timeBounds.mkString(", ")})")
    }
  }

  private def attributeAsText(datableElement: Elem,
                              attributeName: String,
                              label: String): Option[String] =
    (label, datableElement \@ attributeName) match {
      case (_, "")        => None
      case ("", value)    => Some(value)
      case (label, value) => Some(List(label, value).mkString(" "))
    }
}
