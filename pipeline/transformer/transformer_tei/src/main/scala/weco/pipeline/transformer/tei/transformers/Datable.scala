package weco.pipeline.transformer.tei.transformers

import scala.xml.Elem

trait Datable {

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
      case (attribute, label) =>
        attributeAsText(datableElement, attribute, label)
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
