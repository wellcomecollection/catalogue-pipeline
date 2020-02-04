package uk.ac.wellcome.platform.transformer.calm.source

import uk.ac.wellcome.platform.transformer.calm.source.sierra.SierraSourceLocation

case class SierraItemData(
  deleted: Boolean = false,
  location: Option[SierraSourceLocation] = None,
  fixedFields: Map[String, FixedField] = Map(),
  varFields: List[VarField] = List()
)
