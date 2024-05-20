package weco.pipeline.transformer.marc_common.models

sealed trait MarcTaggedField {
  val marcTag: String
}

case class MarcField(
  marcTag: String,
  subfields: Seq[MarcSubfield] = Nil,
  fieldTag: Option[String] = None,
  indicator1: String = " ",
  indicator2: String = " "
) extends MarcTaggedField

case class MarcControlField(
  marcTag: String,
  content: String
) extends MarcTaggedField
