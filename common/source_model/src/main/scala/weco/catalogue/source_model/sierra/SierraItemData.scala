package weco.catalogue.source_model.sierra

import weco.catalogue.source_model.sierra.identifiers.SierraItemNumber
import weco.catalogue.source_model.sierra.source.SierraSourceLocation
import weco.sierra.models.marc.{FixedField, VarField}

case class SierraItemData(
  id: SierraItemNumber,
  deleted: Boolean = false,
  suppressed: Boolean = false,
  copyNo: Option[Int] = None,
  holdCount: Option[Int] = Some(0),
  location: Option[SierraSourceLocation] = None,
  fixedFields: Map[String, FixedField] = Map(),
  varFields: List[VarField] = List()
) {
  require(
    holdCount.getOrElse(0) >= 0,
    s"Item has a negative hold count, how? $holdCount")
}
