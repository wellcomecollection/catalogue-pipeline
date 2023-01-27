package weco.catalogue.source_model.sierra

import weco.sierra.models.identifiers.{
  SierraBibNumber,
  SierraHoldingsNumber,
  SierraItemNumber,
  SierraOrderNumber
}

import java.time.Instant

case class SierraTransformable(
  sierraId: SierraBibNumber,
  maybeBibRecord: Option[SierraBibRecord] = None,
  itemRecords: Map[SierraItemNumber, SierraItemRecord] = Map(),
  holdingsRecords: Map[SierraHoldingsNumber, SierraHoldingsRecord] = Map(),
  orderRecords: Map[SierraOrderNumber, SierraOrderRecord] = Map(),
  modifiedTime: Instant
) {
  // Run some consistency checks on the identifiers.  If these identifiers don't match,
  // it indicates some sort of programming error.
  maybeBibRecord match {
    case Some(bibRecord) => require(bibRecord.id == sierraId)
    case _               => ()
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

  // Check the modifiedTime makes sense.
  //
  // Note: this parameter may not be the max of the modified times of all the
  // included records.  In particular, if there's a record that used to be linked
  // to this transformable, but was later unlinked, the modified time will be
  // taken from the update that unlinked the record.
  private val records = Seq(
    maybeBibRecord
  ).flatten ++ itemRecords.values ++ holdingsRecords.values ++ orderRecords.values
  require(
    records.forall(_.modifiedDate.toEpochMilli <= modifiedTime.toEpochMilli),
    "modifiedTime should be at least as late as all linked records"
  )
}

object SierraTransformable {
  def apply(bibRecord: SierraBibRecord): SierraTransformable =
    SierraTransformable(
      sierraId = bibRecord.id,
      maybeBibRecord = Some(bibRecord),
      modifiedTime = bibRecord.modifiedDate
    )
}
