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

trait RecordOps[Record <: AbstractSierraRecord[_]] extends Logging {
  def getBibIds(r: Record): List[SierraBibNumber]

  def getUnlinkedBibIds(r: Record): List[SierraBibNumber]
}

object RecordOps {
  implicit class SierraRecordOps[Record <: AbstractSierraRecord[_]](r: Record)(
    implicit ops: RecordOps[Record]) {
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
      r: SierraHoldingsRecord): List[SierraBibNumber] =
      r.unlinkedBibIds
  }

  implicit val orderRecordOps = new RecordOps[SierraOrderRecord] {
    override def getBibIds(r: SierraOrderRecord): List[SierraBibNumber] =
      r.bibIds

    override def getUnlinkedBibIds(
      r: SierraOrderRecord): List[SierraBibNumber] =
      r.unlinkedBibIds
  }
}
