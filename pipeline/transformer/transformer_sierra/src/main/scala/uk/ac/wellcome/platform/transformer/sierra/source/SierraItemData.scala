package uk.ac.wellcome.platform.transformer.sierra.source

import uk.ac.wellcome.platform.transformer.sierra.source.sierra.SierraSourceLocation

case class SierraItemData(
  deleted: Boolean = false,
  suppressed: Boolean = false,
  location: Option[SierraSourceLocation] = None,
  callNumber: Option[String] = None,
  fixedFields: Map[String, FixedField] = Map(),
  varFields: List[VarField] = List()
)
