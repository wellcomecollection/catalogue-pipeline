package weco.catalogue.sierra_linker.holdings

import uk.ac.wellcome.sierra_adapter.model.{SierraHoldingsNumber, SierraHoldingsRecord}
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import weco.catalogue.sierra_linker.{LinkingRecord, LinkingRecordStore, LinkingRecordStoreTestCases}

class HoldingsLinkingRecordStoreTest extends LinkingRecordStoreTestCases[SierraHoldingsNumber, SierraHoldingsRecord] with HoldingsLinkerFixtures {
  override def createLinkStore(implicit store: MemoryVersionedStore[SierraHoldingsNumber, LinkingRecord]): LinkingRecordStore[SierraHoldingsNumber, SierraHoldingsRecord] =
    new HoldingsLinkingRecordStore(store)
}
