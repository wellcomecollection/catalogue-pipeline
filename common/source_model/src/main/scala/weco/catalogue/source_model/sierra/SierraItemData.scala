package weco.catalogue.source_model.sierra

import weco.catalogue.source_model.sierra.marc.{FixedField, VarField}
import weco.catalogue.source_model.sierra.source.SierraSourceLocation

case class SierraItemData(
  deleted: Boolean = false,
  suppressed: Boolean = false,
  location: Option[SierraSourceLocation] = None,
  holdCount: Option[Int] = None,
  fixedFields: Map[String, FixedField] = Map(),
  varFields: List[VarField] = List()
)
