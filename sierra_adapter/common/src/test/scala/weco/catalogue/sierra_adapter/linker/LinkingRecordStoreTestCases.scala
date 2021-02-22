package weco.catalogue.sierra_adapter.linker

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, EitherValues}
import uk.ac.wellcome.sierra_adapter.model.{AbstractSierraRecord, SierraGenerators, SierraTypedRecordNumber}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import uk.ac.wellcome.storage.{Identified, StoreWriteError, UpdateWriteError, Version}

trait LinkingRecordStoreTestCases[Id <: SierraTypedRecordNumber, Record <: AbstractSierraRecord[Id]]
  extends AnyFunSpec
    with Matchers
    with EitherValues
    with LinkerFixtures[Id, Record]
    with SierraGenerators
    with SierraAdapterHelpers {

  def createLinkStore(implicit store: MemoryVersionedStore[Id, LinkingRecord]): LinkingRecordStore[Id, Record]

  describe("behaves as a LinkingRecordStore") {
    it("stores a single record") {
      implicit val store = MemoryVersionedStore[Id, LinkingRecord](initialEntries = Map.empty)

      val linkStore = createLinkStore

      val record = createRecord

      linkStore.update(record)

      assertStored(record, version = 0)
    }

    it("does not overwrite new data with old data") {
      val newRecord = createRecordWith(
        modifiedDate = newerDate,
        bibIds = List(createSierraBibNumber)
      )

      implicit val store = MemoryVersionedStore[Id, LinkingRecord](
        initialEntries = Map(
          Version(newRecord.id, 1) -> createLinkingRecord(newRecord)
        )
      )
      val linkStore = createLinkStore

      val oldRecord = createRecordWith(
        id = newRecord.id,
        modifiedDate = olderDate,
        bibIds = List(createSierraBibNumber)
      )
      linkStore.update(oldRecord)

      assertStored(newRecord, version = 1)
    }

    it("overwrites old data with new data") {
      val oldRecord = createRecordWith(
        modifiedDate = olderDate,
        bibIds = List(createSierraBibNumber)
      )

      implicit val store = MemoryVersionedStore[Id, LinkingRecord](
        initialEntries = Map(
          Version(oldRecord.id, 1) -> createLinkingRecord(oldRecord)
        )
      )
      val linkStore = createLinkStore

      val newRecord = createRecordWith(
        id = oldRecord.id,
        modifiedDate = newerDate,
        bibIds = getBibIds(oldRecord) ++ List(createSierraBibNumber)
      )

      linkStore.update(newRecord)

      assertStored(id = oldRecord.id, record = newRecord, version = 2)
    }

    it("records unlinked bibIds") {
      val bibIds = createSierraBibNumbers(count = 3)
      val oldRecord = createRecordWith(
        modifiedDate = olderDate,
        bibIds = bibIds
      )

      implicit val store = MemoryVersionedStore[Id, LinkingRecord](
        initialEntries = Map(
          Version(oldRecord.id, 1) -> createLinkingRecord(oldRecord)
        )
      )
      val linkStore = createLinkStore

      val newRecord = createRecordWith(
        id = oldRecord.id,
        modifiedDate = newerDate,
        bibIds = List(bibIds(0), bibIds(1))
      )

      linkStore.update(newRecord)

      assertStored(
        id = oldRecord.id,
        record = updateRecord(newRecord, unlinkedBibIds = List(bibIds(2))),
        version = 2)
    }

    it("adds new bibIds and records unlinked bibIds in the same update") {
      val bibIds = createSierraBibNumbers(count = 4)

      val oldRecord = createRecordWith(
        modifiedDate = olderDate,
        bibIds = List(bibIds(0), bibIds(1), bibIds(2))
      )

      implicit val store = MemoryVersionedStore[Id, LinkingRecord](
        initialEntries = Map(
          Version(oldRecord.id, 1) -> createLinkingRecord(oldRecord)
        )
      )
      val linkStore = createLinkStore

      val newRecord = createRecordWith(
        id = oldRecord.id,
        modifiedDate = newerDate,
        bibIds = List(bibIds(1), bibIds(2), bibIds(3))
      )

      linkStore.update(newRecord)

      assertStored(
        id = oldRecord.id,
        record = updateRecord(newRecord, unlinkedBibIds = List(bibIds(0))),
        version = 2
      )
    }

    it("preserves existing unlinked bibIds") {
      val bibIds = createSierraBibNumbers(count = 5)

      val oldRecord = createRecordWith(
        modifiedDate = olderDate,
        bibIds = List(bibIds(0), bibIds(1), bibIds(2)),
        unlinkedBibIds = List(bibIds(4))
      )

      implicit val store = MemoryVersionedStore[Id, LinkingRecord](
        initialEntries = Map(
          Version(oldRecord.id, 1) -> createLinkingRecord(oldRecord)
        )
      )
      val linkStore = createLinkStore

      val newRecord = createRecordWith(
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
      val record = createRecord

      val exception = new RuntimeException("AAAAARGH!")

      val brokenStore = new MemoryVersionedStore(
        new MemoryStore[Version[Id, Int], LinkingRecord](
          initialEntries = Map.empty)
          with MemoryMaxima[Id, LinkingRecord]
      ) {
        override def upsert(id: Id)(t: LinkingRecord)(
          f: UpdateFunction): UpdateEither =
          Left(UpdateWriteError(StoreWriteError(exception)))
      }

      val linkStore = createLinkStore(brokenStore)

      val either = linkStore.update(record)
      either shouldBe a[Left[_, _]]
      either.left.get shouldBe exception
    }
  }

  private def assertStored(id: Id,
                           record: Record,
                           version: Int)(
    implicit store: MemoryVersionedStore[Id, LinkingRecord]): Assertion =
    store.getLatest(id).value shouldBe Identified(
      Version(record.id, version),
      createLinkingRecord(record))

  private def assertStored(record: Record, version: Int)(
    implicit store: MemoryVersionedStore[Id, LinkingRecord])
  : Assertion =
    assertStored(id = record.id, record = record, version = version)
}
