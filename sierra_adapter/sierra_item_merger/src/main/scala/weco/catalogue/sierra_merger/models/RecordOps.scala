package weco.catalogue.sierra_merger.models

import grizzled.slf4j.Logging
import uk.ac.wellcome.sierra_adapter.model.{AbstractSierraRecord, SierraBibNumber, SierraItemRecord}

trait RecordOps[Record <: AbstractSierraRecord[_]] extends Logging {
  def getBibIds(r: Record): List[SierraBibNumber]

  def getUnlinkedBibIds(r: Record): List[SierraBibNumber]
}

object RecordOps {
  implicit class SierraRecordOps[Record <: AbstractSierraRecord[_]](r: Record)(implicit ops: RecordOps[Record]) {
    def linkedBibIds: List[SierraBibNumber] =
      ops.getBibIds(r)

    def unlinkedBibIds: List[SierraBibNumber] =
      ops.getUnlinkedBibIds(r)
  }

  implicit val itemRecordOps = new RecordOps[SierraItemRecord] {
    override def getBibIds(itemRecord: SierraItemRecord): List[SierraBibNumber] =
      itemRecord.bibIds

    override def getUnlinkedBibIds(itemRecord: SierraItemRecord): List[SierraBibNumber] =
      itemRecord.unlinkedBibIds
  }
}
