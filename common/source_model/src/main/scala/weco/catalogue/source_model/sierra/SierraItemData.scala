package weco.catalogue.source_model.sierra

import weco.catalogue.source_model.sierra.marc.{FixedField, VarField}
import weco.catalogue.source_model.sierra.source.SierraSourceLocation

case class SierraItemData(
  deleted: Boolean = false,
  suppressed: Boolean = false,
  location: Option[SierraSourceLocation] = None,
  holdCount: Option[Int] = Some(0),
  fixedFields: Map[String, FixedField] = Map(),
  varFields: List[VarField] = List()
) {
  require(holdCount.getOrElse(0) >= 0, s"Item has a negative hold count, how? $holdCount")
}
