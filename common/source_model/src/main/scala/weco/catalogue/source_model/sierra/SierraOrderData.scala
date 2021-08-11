package weco.catalogue.source_model.sierra

import weco.sierra.models.marc.FixedField

case class SierraOrderData(
  deleted: Boolean = false,
  suppressed: Boolean = false,
  fixedFields: Map[String, FixedField] = Map()
)
