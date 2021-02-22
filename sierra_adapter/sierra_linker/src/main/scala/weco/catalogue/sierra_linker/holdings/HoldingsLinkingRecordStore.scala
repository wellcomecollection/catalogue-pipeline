package weco.catalogue.sierra_linker.holdings

import uk.ac.wellcome.sierra_adapter.model.{
  SierraBibNumber,
  SierraHoldingsNumber,
  SierraHoldingsRecord
}
import uk.ac.wellcome.storage.store.VersionedStore
import weco.catalogue.sierra_linker.{LinkingRecord, LinkingRecordStore}

class HoldingsLinkingRecordStore(
  val store: VersionedStore[SierraHoldingsNumber, Int, LinkingRecord])
    extends LinkingRecordStore[SierraHoldingsNumber, SierraHoldingsRecord] {
  override def createNewLink(
    holdingsRecord: SierraHoldingsRecord): LinkingRecord =
    LinkingRecord(holdingsRecord)

  override def updateLink(
    existingLink: LinkingRecord,
    holdingsRecord: SierraHoldingsRecord): Option[LinkingRecord] =
    existingLink.update(holdingsRecord)

  override def updateRecord(
    holdingsRecord: SierraHoldingsRecord,
    unlinkedBibIds: List[SierraBibNumber]): SierraHoldingsRecord =
    holdingsRecord.copy(unlinkedBibIds = unlinkedBibIds)
}
