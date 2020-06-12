package uk.ac.wellcome.platform.sierra_item_merger.store

import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.sierra_adapter.model.{SierraGenerators, SierraItemRecord}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.Version

class ItemStoreTest
    extends AnyFunSpec
    with SierraAdapterHelpers
    with SierraGenerators {
  it("retrieves a record from the store") {
    val itemRecord = createSierraItemRecord
    val key = Version(itemRecord.id.withoutCheckDigit, 0)
    val itemRecordStore =
      new ItemStore(createStore[SierraItemRecord](Map(key -> itemRecord)))
    itemRecordStore.get(key) shouldBe Right(itemRecord)
  }

  it("returns an exception if the record does not exist in the store") {
    val itemRecord = createSierraItemRecord
    val key = Version(itemRecord.id.withoutCheckDigit, 0)
    val itemRecordStore = new ItemStore(createStore[SierraItemRecord]())
    val result = itemRecordStore.get(key)
    result shouldBe a[Left[_, _]]
    result.left.get shouldBe a[Throwable]
  }

  it("returns an exception if the record exists with a lower version") {
    val itemRecord = createSierraItemRecord
    val lowerVersionKey = Version(itemRecord.id.withoutCheckDigit, 0)
    val itemRecordStore = new ItemStore(
      createStore[SierraItemRecord](Map(lowerVersionKey -> itemRecord)))
    val result = itemRecordStore.get(lowerVersionKey.copy(version = 1))
    result shouldBe a[Left[_, _]]
    result.left.get shouldBe a[Throwable]
  }

  it(
    "returns VersionExpectedConflictException if the record exists with a higher version") {
    val itemRecord = createSierraItemRecord
    val higherVersionKey = Version(itemRecord.id.withoutCheckDigit, 1)
    val itemRecordStore = new ItemStore(
      createStore[SierraItemRecord](Map(higherVersionKey -> itemRecord)))
    val result = itemRecordStore.get(higherVersionKey.copy(version = 0))
    result shouldBe a[Left[_, _]]
    result.left.get shouldBe a[VersionExpectedConflictException]
  }
}
