package weco.catalogue.sierra_linker.items

import uk.ac.wellcome.sierra_adapter.model.{SierraItemNumber, SierraItemRecord}
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import weco.catalogue.sierra_linker.{
  LinkingRecord,
  LinkingRecordStore,
  LinkingRecordStoreTestCases
}

class ItemLinkingRecordStoreTest
    extends LinkingRecordStoreTestCases[SierraItemNumber, SierraItemRecord]
    with ItemLinkerFixtures {
  override def createLinkStore(
    implicit store: MemoryVersionedStore[SierraItemNumber, LinkingRecord])
    : LinkingRecordStore[SierraItemNumber, SierraItemRecord] =
    new ItemLinkingRecordStore(store)
}
