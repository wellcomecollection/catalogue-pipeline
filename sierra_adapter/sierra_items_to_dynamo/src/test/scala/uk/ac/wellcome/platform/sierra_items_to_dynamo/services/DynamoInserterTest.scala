package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import uk.ac.wellcome.sierra_adapter.model.{SierraGenerators, SierraItemRecord}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.maxima.Maxima
import uk.ac.wellcome.storage.maxima.memory.MemoryMaxima
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryTypedStore}
import uk.ac.wellcome.storage.store.{
  HybridStoreWithMaxima,
  Store,
  TypedStore,
  VersionedHybridStore
}
import uk.ac.wellcome.storage.{StoreWriteError, UpdateWriteError, Version}
import weco.catalogue.source_model.fixtures.SourceVHSFixture
import weco.catalogue.source_model.store.SourceVHS

class DynamoInserterTest
    extends AnyFunSpec
    with Matchers
    with SierraGenerators
    with SierraAdapterHelpers
    with SourceVHSFixture {

  it("inserts an ItemRecord into the VHS") {
    val sourceVHS = createSourceVHS[SierraItemRecord]
    val dynamoInserter = new DynamoInserter(sourceVHS)

    val record = createSierraItemRecord

    dynamoInserter.insertIntoDynamo(record)

    assertStored(record.id.withoutCheckDigit, record, sourceVHS)
  }

  it("does not overwrite new data with old data") {
    val newRecord = createSierraItemRecordWith(
      modifiedDate = newerDate,
      bibIds = List(createSierraBibNumber)
    )

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(newRecord.id.withoutCheckDigit, 1) -> newRecord
      )
    )
    val dynamoInserter = new DynamoInserter(sourceVHS)

    val oldRecord = createSierraItemRecordWith(
      id = newRecord.id,
      modifiedDate = olderDate,
      bibIds = List(createSierraBibNumber)
    )
    dynamoInserter.insertIntoDynamo(oldRecord)

    assertStored(oldRecord.id.withoutCheckDigit, newRecord, sourceVHS)
  }

  it("overwrites old data with new data") {
    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(createSierraBibNumber)
    )

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(oldRecord.id.withoutCheckDigit, 1) -> oldRecord
      )
    )
    val dynamoInserter = new DynamoInserter(sourceVHS)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = oldRecord.bibIds ++ List(createSierraBibNumber)
    )

    dynamoInserter.insertIntoDynamo(newRecord)

    assertStored[SierraItemRecord](oldRecord.id.withoutCheckDigit, newRecord, sourceVHS)
  }

  it("records unlinked bibIds") {
    val bibIds = createSierraBibNumbers(count = 3)
    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = bibIds
    )

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(oldRecord.id.withoutCheckDigit, 1) -> oldRecord
      )
    )
    val dynamoInserter = new DynamoInserter(sourceVHS)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = List(bibIds(0), bibIds(1))
    )

    dynamoInserter.insertIntoDynamo(newRecord)

    assertStored[SierraItemRecord](
      oldRecord.id.withoutCheckDigit,
      newRecord.copy(unlinkedBibIds = List(bibIds(2))),
      sourceVHS
    )
  }

  it("adds new bibIds and records unlinked bibIds in the same update") {
    val bibIds = createSierraBibNumbers(count = 4)

    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(bibIds(0), bibIds(1), bibIds(2))
    )

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(oldRecord.id.withoutCheckDigit, 1) -> oldRecord
      )
    )
    val dynamoInserter = new DynamoInserter(sourceVHS)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = List(bibIds(1), bibIds(2), bibIds(3))
    )

    dynamoInserter.insertIntoDynamo(newRecord)

    assertStored[SierraItemRecord](
      id = oldRecord.id.withoutCheckDigit,
      newRecord.copy(unlinkedBibIds = List(bibIds(0))),
      sourceVHS
    )
  }

  it("preserves existing unlinked bibIds in DynamoDB") {
    val bibIds = createSierraBibNumbers(count = 5)

    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(bibIds(0), bibIds(1), bibIds(2)),
      unlinkedBibIds = List(bibIds(4))
    )

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(oldRecord.id.withoutCheckDigit, 1) -> oldRecord
      )
    )
    val dynamoInserter = new DynamoInserter(sourceVHS)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = List(bibIds(1), bibIds(2), bibIds(3)),
      unlinkedBibIds = List()
    )

    dynamoInserter.insertIntoDynamo(newRecord)

    val actualRecord =
      sourceVHS.underlying.getLatest(oldRecord.id.withoutCheckDigit).right.get.identifiedT

    actualRecord.unlinkedBibIds shouldBe List(bibIds(4), bibIds(0))
  }

  class BrokenStore extends HybridStoreWithMaxima[String, Int, S3ObjectLocation, SierraItemRecord] {
    override protected def createTypeStoreId(id: Version[String, Int]): S3ObjectLocation =
      createS3ObjectLocation

    implicit override val indexedStore: Store[Version[String, Int], S3ObjectLocation] with Maxima[String, Version[String, Int], S3ObjectLocation] =
      new MemoryStore[Version[String, Int], S3ObjectLocation](
        initialEntries = Map.empty)
        with MemoryMaxima[String, S3ObjectLocation]

    override implicit val typedStore: TypedStore[S3ObjectLocation, SierraItemRecord] =
      MemoryTypedStore[S3ObjectLocation, SierraItemRecord]()
  }

  it("fails if the VHS returns an error when updating an item") {
    val record = createSierraItemRecordWith(
      modifiedDate = newerDate
    )
    val exception = new RuntimeException("AAAAARGH!")
    val brokenStore = new VersionedHybridStore(new BrokenStore) {
      override def upsert(id: String)(t: SierraItemRecord)(f: UpdateFunction): UpdateEither =
        Left(UpdateWriteError(StoreWriteError(exception)))
    }

    val sourceVHS = new SourceVHS[SierraItemRecord](brokenStore)
    val dynamoInserter = new DynamoInserter(sourceVHS)

    val either = dynamoInserter.insertIntoDynamo(record)
    either shouldBe a[Left[_, _]]
    either.left.get shouldBe exception
  }

}
