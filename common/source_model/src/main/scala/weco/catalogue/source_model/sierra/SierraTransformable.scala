package weco.catalogue.source_model.sierra

import weco.sierra.models.identifiers.{
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
) {
  // Run some consistency checks on the identifiers.  If these identifiers don't match,
  // it indicates some sort of programming error.
  maybeBibRecord match {
    case Some(bibRecord) => require(bibRecord.id == sierraId)
    case _ => ()
  }

  itemRecords.foreach { case (id, record) =>
    require(record.id == id)
    require(record.bibIds.contains(sierraId))
  }
  holdingsRecords.foreach { case (id, record) =>
    require(record.id == id)
    require(record.bibIds.contains(sierraId))
  }
  orderRecords.foreach { case (id, record) =>
    require(record.id == id)
    require(record.bibIds.contains(sierraId))
  }
}

object SierraTransformable {
  def apply(bibRecord: SierraBibRecord): SierraTransformable =
    SierraTransformable(
      sierraId = bibRecord.id,
      maybeBibRecord = Some(bibRecord))

  def createNew(
    sierraId: SierraBibNumber,
    itemRecords: Seq[SierraItemRecord] = List(),
    holdingsRecords: Seq[SierraHoldingsRecord] = List(),
    orderRecords: Seq[SierraOrderRecord] = List()
  ): SierraTransformable =
    SierraTransformable(
      sierraId = sierraId,
      maybeBibRecord = None,
      itemRecords = itemRecords.map { record => record.id -> record }.toMap,
      holdingsRecords = holdingsRecords.map { record => record.id -> record }.toMap,
      orderRecords = orderRecords.map { record => record.id -> record }.toMap,
    )

  def createNew2(
    bibRecord: SierraBibRecord,
    itemRecords: Seq[SierraItemRecord] = List(),
    holdingsRecords: Seq[SierraHoldingsRecord] = List(),
    orderRecords: Seq[SierraOrderRecord] = List()
  ): SierraTransformable =
    SierraTransformable(
      sierraId = bibRecord.id,
      maybeBibRecord = Some(bibRecord),
      itemRecords = itemRecords.map { record => record.id -> record }.toMap,
      holdingsRecords = holdingsRecords.map { record => record.id -> record }.toMap,
      orderRecords = orderRecords.map { record => record.id -> record }.toMap,
    )
}
