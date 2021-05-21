package weco.catalogue.source_model.sierra.marc

import io.circe.generic.extras.JsonKey

case class VarField(
  content: Option[String] = None,
  marcTag: Option[String] = None,
  fieldTag: Option[String] = None,
  @JsonKey("ind1") indicator1: Option[String] = None,
  @JsonKey("ind2") indicator2: Option[String] = None,
  subfields: List[MarcSubfield] = Nil
)
