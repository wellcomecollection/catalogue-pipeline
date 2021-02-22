package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import uk.ac.wellcome.sierra_adapter.model.{
  SierraBibNumber,
  SierraItemNumber,
  SierraItemRecord
}
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import weco.catalogue.sierra_adapter.linker.{
  LinkingRecord,
  LinkingRecordStore,
  LinkingRecordStoreTestCases
}

import java.time.Instant

class ItemLinkingRecordStoreTest extends LinkingRecordStoreTestCases[SierraItemNumber, SierraItemRecord] {
  override def createId: SierraItemNumber = createSierraItemNumber

  override def createRecordWith(id: SierraItemNumber, modifiedDate: Instant, bibIds: List[SierraBibNumber], unlinkedBibIds: List[SierraBibNumber]): SierraItemRecord =
    createSierraItemRecordWith(
      id = id,
      modifiedDate = modifiedDate,
      bibIds = bibIds,
      unlinkedBibIds = unlinkedBibIds
    )

  override def createLinkingRecord(itemRecord: SierraItemRecord): LinkingRecord =
    LinkingRecord(itemRecord)

  override def createLinkStore(implicit store: MemoryVersionedStore[SierraItemNumber, LinkingRecord]): LinkingRecordStore[SierraItemNumber, SierraItemRecord] =
    new ItemLinkingRecordStore(store)

  override def getBibIds(itemRecord: SierraItemRecord): List[SierraBibNumber] = itemRecord.bibIds

  override def updateRecord(itemRecord: SierraItemRecord, unlinkedBibIds: List[SierraBibNumber]): SierraItemRecord =
    itemRecord.copy(unlinkedBibIds = unlinkedBibIds)
}
