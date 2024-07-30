package weco.pipeline.sierra_merger.models

import grizzled.slf4j.Logging
import weco.catalogue.source_model.sierra.{
  AbstractSierraRecord,
  SierraBibRecord,
  SierraHoldingsRecord,
  SierraItemRecord,
  SierraOrderRecord
}
import weco.sierra.models.identifiers.SierraBibNumber

trait RecordOps[SierraRecord <: AbstractSierraRecord[_]] extends Logging {
  def getBibIds(r: SierraRecord): List[SierraBibNumber]

  def getUnlinkedBibIds(r: SierraRecord): List[SierraBibNumber]
}

object RecordOps {
  implicit class SierraRecordOps[SierraRecord <: AbstractSierraRecord[_]](
    r: SierraRecord
  )(
    implicit ops: RecordOps[SierraRecord]
  ) {
    def linkedBibIds: List[SierraBibNumber] =
      ops.getBibIds(r)

    def unlinkedBibIds: List[SierraBibNumber] =
      ops.getUnlinkedBibIds(r)
  }

  implicit val bibRecordOps = new RecordOps[SierraBibRecord] {
    override def getBibIds(r: SierraBibRecord): List[SierraBibNumber] =
      List(r.id)

    override def getUnlinkedBibIds(r: SierraBibRecord): List[SierraBibNumber] =
      List()
  }

  implicit val itemRecordOps = new RecordOps[SierraItemRecord] {
    override def getBibIds(r: SierraItemRecord): List[SierraBibNumber] =
      r.bibIds

    override def getUnlinkedBibIds(r: SierraItemRecord): List[SierraBibNumber] =
      r.unlinkedBibIds
  }

  implicit val holdingsRecordOps = new RecordOps[SierraHoldingsRecord] {
    override def getBibIds(r: SierraHoldingsRecord): List[SierraBibNumber] =
      r.bibIds

    override def getUnlinkedBibIds(
      r: SierraHoldingsRecord
    ): List[SierraBibNumber] =
      r.unlinkedBibIds
  }

  implicit val orderRecordOps = new RecordOps[SierraOrderRecord] {
    override def getBibIds(r: SierraOrderRecord): List[SierraBibNumber] =
      r.bibIds

    override def getUnlinkedBibIds(
      r: SierraOrderRecord
    ): List[SierraBibNumber] =
      r.unlinkedBibIds
  }
}
