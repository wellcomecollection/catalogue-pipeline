package weco.catalogue.sierra_merger.models

import grizzled.slf4j.Logging
import uk.ac.wellcome.sierra_adapter.model.{
  AbstractSierraRecord,
  SierraBibNumber,
  SierraBibRecord,
  SierraItemRecord
}

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
    override def getBibIds(r: SierraBibRecord): List[SierraBibNumber] = List(r.id)

    override def getUnlinkedBibIds(r: SierraBibRecord): List[SierraBibNumber] = List()
  }

  implicit val itemRecordOps = new RecordOps[SierraItemRecord] {
    override def getBibIds(r: SierraItemRecord): List[SierraBibNumber] =
      r.bibIds

    override def getUnlinkedBibIds(r: SierraItemRecord): List[SierraBibNumber] =
      r.unlinkedBibIds
  }
}
