package uk.ac.wellcome.platform.transformer.sierra.source

case class SierraHoldingsData(
  deleted: Boolean = false,
  suppressed: Boolean = false,
  fixedFields: Map[String, FixedField] = Map(),
  varFields: List[VarField] = List()
)
