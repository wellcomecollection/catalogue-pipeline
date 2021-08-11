package weco.catalogue.source_model.sierra

import weco.sierra.models.fields.SierraLocation
import weco.sierra.models.identifiers.SierraItemNumber
import weco.sierra.models.marc.{FixedField, VarField}

case class SierraItemData(
  id: SierraItemNumber,
  deleted: Boolean = false,
  suppressed: Boolean = false,
  copyNo: Option[Int] = None,
  holdCount: Option[Int] = Some(0),
  location: Option[SierraLocation] = None,
  fixedFields: Map[String, FixedField] = Map(),
  varFields: List[VarField] = List()
) {
  require(
    holdCount.getOrElse(0) >= 0,
    s"Item has a negative hold count, how? $holdCount")
}
