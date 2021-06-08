package weco.catalogue.source_model.sierra

import weco.catalogue.source_model.sierra.identifiers.{
  SierraBibNumber,
  SierraHoldingsNumber,
  SierraItemNumber,
  SierraOrderNumber
}

case class SierraTransformable(
  sierraId: SierraBibNumber,
  maybeBibRecord: Option[SierraBibRecord] = None,
  itemRecords: Map[SierraItemNumber, SierraItemRecord] = Map(),
  holdingsRecords: Map[SierraHoldingsNumber, SierraHoldingsRecord] = Map(),
  orderRecords: Map[SierraOrderNumber, SierraOrderRecord] = Map()
)

object SierraTransformable {
  def apply(bibRecord: SierraBibRecord): SierraTransformable =
    SierraTransformable(
      sierraId = bibRecord.id,
      maybeBibRecord = Some(bibRecord))
}
