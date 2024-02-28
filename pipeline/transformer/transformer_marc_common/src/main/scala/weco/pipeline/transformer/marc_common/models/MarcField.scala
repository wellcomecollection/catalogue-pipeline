package weco.pipeline.transformer.marc_common.models

case class MarcField(
  marcTag: String,
  subfields: Seq[MarcSubfield] = Nil,
  content: Option[String] = None,
  fieldTag: Option[String] = None,
  indicator1: String = " ",
  indicator2: String = " "
)
