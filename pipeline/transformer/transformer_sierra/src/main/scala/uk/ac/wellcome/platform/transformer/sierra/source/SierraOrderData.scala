package uk.ac.wellcome.platform.transformer.sierra.source

case class SierraOrderData(
  deleted: Boolean = false,
  suppressed: Boolean = false,
  fixedFields: Map[String, FixedField] = Map()
)
