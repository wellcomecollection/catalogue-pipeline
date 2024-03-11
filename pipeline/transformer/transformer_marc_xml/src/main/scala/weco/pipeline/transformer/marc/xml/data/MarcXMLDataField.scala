package weco.pipeline.transformer.marc.xml.data

import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

import scala.xml.Node

object MarcXMLDataField {
  def apply(elem: Node): MarcField =
    MarcField(
      marcTag = elem \@ "tag",
      indicator1 = elem \@ "ind1",
      indicator2 = elem \@ "ind2",
      subfields = elem \ "subfield" map (MarcXMLSubfield(_))
    )
}

object MarcXMLSubfield {
  def apply(elem: Node): MarcSubfield =
    MarcSubfield(tag = elem \@ "code", content = elem.text)
}
