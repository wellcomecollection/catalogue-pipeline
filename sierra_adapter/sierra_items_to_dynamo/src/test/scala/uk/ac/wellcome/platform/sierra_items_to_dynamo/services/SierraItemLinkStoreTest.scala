package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, EitherValues}
import uk.ac.wellcome.platform.sierra_items_to_dynamo.models.SierraItemLink
import uk.ac.wellcome.sierra_adapter.model.{
  SierraGenerators,
  SierraItemNumber,
  SierraItemRecord
}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import uk.ac.wellcome.storage.{
  Identified,
  StoreWriteError,
  UpdateWriteError,
  Version
}

class SierraItemLinkStoreTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with SierraGenerators
    with SierraAdapterHelpers {

  it("inserts an ItemRecord into the VHS") {
    implicit val store = MemoryVersionedStore[SierraItemNumber, SierraItemLink](
      initialEntries = Map.empty)
    val linkStore = new SierraItemLinkStore(store)

    val record = createSierraItemRecord

    linkStore.update(record)

    assertStored(record, version = 0)
  }

  it("does not overwrite new data with old data") {
    val newRecord = createSierraItemRecordWith(
      modifiedDate = newerDate,
      bibIds = List(createSierraBibNumber)
    )

    implicit val store = MemoryVersionedStore[SierraItemNumber, SierraItemLink](
      initialEntries = Map(
        Version(newRecord.id, 1) -> SierraItemLink(newRecord)
      )
    )
    val linkStore = new SierraItemLinkStore(store)

    val oldRecord = createSierraItemRecordWith(
      id = newRecord.id,
      modifiedDate = olderDate,
      bibIds = List(createSierraBibNumber)
    )
    linkStore.update(oldRecord)

    assertStored(newRecord, version = 1)
  }

  it("overwrites old data with new data") {
    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(createSierraBibNumber)
    )

    implicit val store = MemoryVersionedStore[SierraItemNumber, SierraItemLink](
      initialEntries = Map(
        Version(oldRecord.id, 1) -> SierraItemLink(oldRecord)
      )
    )
    val linkStore = new SierraItemLinkStore(store)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = oldRecord.bibIds ++ List(createSierraBibNumber)
    )

    linkStore.update(newRecord)

    assertStored(id = oldRecord.id, record = newRecord, version = 2)
  }

  it("records unlinked bibIds") {
    val bibIds = createSierraBibNumbers(count = 3)
    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = bibIds
    )

    implicit val store = MemoryVersionedStore[SierraItemNumber, SierraItemLink](
      initialEntries = Map(
        Version(oldRecord.id, 1) -> SierraItemLink(oldRecord)
      )
    )
    val linkStore = new SierraItemLinkStore(store)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = List(bibIds(0), bibIds(1))
    )

    linkStore.update(newRecord)

    assertStored(
      id = oldRecord.id,
      record = newRecord.copy(unlinkedBibIds = List(bibIds(2))),
      version = 2)
  }

  it("adds new bibIds and records unlinked bibIds in the same update") {
    val bibIds = createSierraBibNumbers(count = 4)

    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(bibIds(0), bibIds(1), bibIds(2))
    )

    implicit val store = MemoryVersionedStore[SierraItemNumber, SierraItemLink](
      initialEntries = Map(
        Version(oldRecord.id, 1) -> SierraItemLink(oldRecord)
      )
    )
    val linkStore = new SierraItemLinkStore(store)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = List(bibIds(1), bibIds(2), bibIds(3))
    )

    linkStore.update(newRecord)

    assertStored(
      id = oldRecord.id,
      record = newRecord.copy(unlinkedBibIds = List(bibIds(0))),
      version = 2
    )
  }

  it("preserves existing unlinked bibIds in DynamoDB") {
    val bibIds = createSierraBibNumbers(count = 5)

    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(bibIds(0), bibIds(1), bibIds(2)),
      unlinkedBibIds = List(bibIds(4))
    )

    implicit val store = MemoryVersionedStore[SierraItemNumber, SierraItemLink](
      initialEntries = Map(
        Version(oldRecord.id, 1) -> SierraItemLink(oldRecord)
      )
    )
    val linkStore = new SierraItemLinkStore(store)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = List(bibIds(1), bibIds(2), bibIds(3)),
      unlinkedBibIds = List()
    )

    linkStore.update(newRecord)

    val actualRecord =
      store
        .getLatest(oldRecord.id)
        .right
        .get
        .identifiedT

    actualRecord.unlinkedBibIds shouldBe List(bibIds(4), bibIds(0))
  }

  it("fails if the store returns an error when updating an item") {
    val record = createSierraItemRecordWith(
      modifiedDate = newerDate
    )

    val exception = new RuntimeException("AAAAARGH!")

    val brokenStore = new MemoryVersionedStore(
      new MemoryStore[Version[SierraItemNumber, Int], SierraItemLink](
        initialEntries = Map.empty)
      with MemoryMaxima[SierraItemNumber, SierraItemLink]
    ) {
      override def upsert(id: SierraItemNumber)(t: SierraItemLink)(
        f: UpdateFunction): UpdateEither =
        Left(UpdateWriteError(StoreWriteError(exception)))
    }

    val linkStore = new SierraItemLinkStore(brokenStore)

    val either = linkStore.update(record)
    either shouldBe a[Left[_, _]]
    either.left.get shouldBe exception
  }

  private def assertStored(id: SierraItemNumber,
                           record: SierraItemRecord,
                           version: Int)(
    implicit store: MemoryVersionedStore[SierraItemNumber, SierraItemLink])
    : Assertion =
    store.getLatest(id).value shouldBe Identified(
      Version(record.id, version),
      SierraItemLink(record))

  private def assertStored(record: SierraItemRecord, version: Int)(
    implicit store: MemoryVersionedStore[SierraItemNumber, SierraItemLink])
    : Assertion =
    assertStored(id = record.id, record = record, version = version)
}
