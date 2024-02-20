package weco.pipeline.transformer.ebsco.data

import scala.xml.Node

case class DataField(
  marcTag: String,
  indicator1: String = " ",
  indicator2: String = " ",
  subfields: Seq[Subfield] = Nil
)

object DataField {
  def apply(elem: Node): DataField =
    DataField(
      marcTag = elem \@ "tag",
      indicator1 = elem \@ "ind1",
      indicator2 = elem \@ "ind2",
      subfields = elem \ "subfield" map (Subfield(_))
    )
}

// TODO: This is the same as the Sierra one.
case class Subfield(
  tag: String,
  content: String
)

object Subfield {
  def apply(elem: Node): Subfield =
    Subfield(tag = elem \@ "tag", content = elem.text)
}
