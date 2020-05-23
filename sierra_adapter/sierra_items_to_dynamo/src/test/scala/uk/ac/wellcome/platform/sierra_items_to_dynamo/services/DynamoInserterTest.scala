package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.ac.wellcome.platform.sierra_items_to_dynamo.fixtures.DynamoInserterFixture
import uk.ac.wellcome.sierra_adapter.model.{SierraGenerators, SierraItemRecord}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryVersionedStore}
import uk.ac.wellcome.storage.{UpdateNotApplied, Version}

class DynamoInserterTest
  extends AnyFunSpec
    with Matchers
    with MockitoSugar
    with DynamoInserterFixture
    with SierraGenerators with SierraAdapterHelpers {

  it("inserts an ItemRecord into the VHS") {
    val store = createStore[SierraItemRecord]()
    withDynamoInserter(store) { dynamoInserter =>
      val record = createSierraItemRecord

      dynamoInserter.insertIntoDynamo(record)

      assertStored[SierraItemRecord](
        id = record.id.withoutCheckDigit,
        t = record,
        store = store
      )
    }
  }


  it("does not overwrite new data with old data") {
    val newRecord = createSierraItemRecordWith(
      modifiedDate = newerDate,
      bibIds = List(createSierraBibNumber)
    )
    val store = createStore(Map(Version(newRecord.id.withoutCheckDigit, 1) -> newRecord))
    val dynamoInserter = new DynamoInserter(store)

    val oldRecord = createSierraItemRecordWith(
      id = newRecord.id,
      modifiedDate = olderDate,
      bibIds = List(createSierraBibNumber)
    )
    dynamoInserter.insertIntoDynamo(oldRecord)

    assertStored[SierraItemRecord](
      oldRecord.id.withoutCheckDigit,
      newRecord,
      store
    )
  }


  it("overwrites old data with new data") {
    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(createSierraBibNumber)
    )
    val store = createStore(Map(Version(oldRecord.id.withoutCheckDigit, 1) -> oldRecord))
    val dynamoInserter = new DynamoInserter(store)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = oldRecord.bibIds ++ List(createSierraBibNumber)
    )

    dynamoInserter.insertIntoDynamo(newRecord)

    assertStored[SierraItemRecord](
      oldRecord.id.withoutCheckDigit,
      newRecord,
      store
    )


  }

  it("records unlinked bibIds") {
    val bibIds = createSierraBibNumbers(count = 3)
    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = bibIds
    )
    val store = createStore(Map(Version(oldRecord.id.withoutCheckDigit, 1) -> oldRecord))

    val dynamoInserter = new DynamoInserter(store)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = List(bibIds(0), bibIds(1))
    )

    dynamoInserter.insertIntoDynamo(newRecord)

    assertStored[SierraItemRecord](
      oldRecord.id.withoutCheckDigit,
      newRecord.copy(unlinkedBibIds = List(bibIds(2))),
      store
    )

  }


  it("adds new bibIds and records unlinked bibIds in the same update") {

    val bibIds = createSierraBibNumbers(count = 4)

    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(bibIds(0), bibIds(1), bibIds(2))
    )
    val store = createStore(Map(Version(oldRecord.id.withoutCheckDigit, 1) -> oldRecord))
    val dynamoInserter = new DynamoInserter(store)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = List(bibIds(1), bibIds(2), bibIds(3))
    )

    dynamoInserter.insertIntoDynamo(newRecord)

    assertStored[SierraItemRecord](
      id = oldRecord.id.withoutCheckDigit,
      newRecord.copy(unlinkedBibIds = List(bibIds(0))),
      store
    )
  }

  it("preserves existing unlinked bibIds in DynamoDB") {

    val bibIds = createSierraBibNumbers(count = 5)

    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(bibIds(0), bibIds(1), bibIds(2)),
      unlinkedBibIds = List(bibIds(4))
    )
    val store = createStore(Map(Version(oldRecord.id.withoutCheckDigit, 1) -> oldRecord))
    val dynamoInserter = new DynamoInserter(store)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = List(bibIds(1), bibIds(2), bibIds(3)),
      unlinkedBibIds = List()
    )

    dynamoInserter.insertIntoDynamo(newRecord)

    val actualRecord = store.getLatest(oldRecord.id.withoutCheckDigit).right.get.identifiedT

    actualRecord.unlinkedBibIds shouldBe List(bibIds(4), bibIds(0))
  }

  it("fails if the VHS returns an error when updating an item") {
    val record = createSierraItemRecordWith(
      modifiedDate = newerDate
    )
    val exception = new RuntimeException("AAAAARGH!")
    val failingStore = new MemoryVersionedStore(new MemoryStore(Map[Version[String, Int], SierraItemRecord]()) with MemoryMaxima[String, SierraItemRecord]) {
      override def upsert(id: String)(t: SierraItemRecord)(f: UpdateFunction): UpdateEither = {
        Left(UpdateNotApplied(exception))
      }
    }

    withDynamoInserter(failingStore) { dynamoInserter =>

      val either = dynamoInserter.insertIntoDynamo(record)
      either shouldBe a[Left[_,_]]
      either.left.get shouldBe exception
    }
  }

}
