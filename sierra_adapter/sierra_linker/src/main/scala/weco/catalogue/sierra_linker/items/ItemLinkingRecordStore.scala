package weco.catalogue.sierra_linker.items

import uk.ac.wellcome.sierra_adapter.model.{
  SierraBibNumber,
  SierraItemNumber,
  SierraItemRecord
}
import uk.ac.wellcome.storage.store.VersionedStore
import weco.catalogue.sierra_linker.{LinkingRecord, LinkingRecordStore}

class ItemLinkingRecordStore(
  val store: VersionedStore[SierraItemNumber, Int, LinkingRecord])
    extends LinkingRecordStore[SierraItemNumber, SierraItemRecord] {

  override def createNewLink(itemRecord: SierraItemRecord): LinkingRecord =
    LinkingRecord(itemRecord)

  override def updateLink(existingLink: LinkingRecord,
                          itemRecord: SierraItemRecord): Option[LinkingRecord] =
    existingLink.update(itemRecord)

  override def updateRecord(
    itemRecord: SierraItemRecord,
    unlinkedBibIds: List[SierraBibNumber]): SierraItemRecord =
    itemRecord.copy(unlinkedBibIds = unlinkedBibIds)
}
