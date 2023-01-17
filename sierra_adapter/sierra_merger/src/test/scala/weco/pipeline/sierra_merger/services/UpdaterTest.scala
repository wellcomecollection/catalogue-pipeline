package weco.pipeline.sierra_merger.services

import org.scalatest.{Assertion, EitherValues}
import org.scalatest.funspec.AnyFunSpec
import weco.storage.maxima.Maxima
import weco.storage.maxima.memory.MemoryMaxima
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.memory.{MemoryStore, MemoryTypedStore}
import weco.storage.store.{
  HybridStoreWithMaxima,
  Store,
  TypedStore,
  VersionedHybridStore
}
import weco.storage.{StoreWriteError, UpdateWriteError, Version}
import weco.catalogue.source_model.fixtures.SourceVHSFixture
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.catalogue.source_model.sierra.{
  SierraItemRecord,
  SierraTransformable
}
import weco.catalogue.source_model.store.SourceVHS
import weco.catalogue.source_model.Implicits._
import weco.pipeline.sierra_merger.fixtures.RecordMergerFixtures
import weco.sierra.models.identifiers.TypedSierraRecordNumber

class UpdaterTest
    extends AnyFunSpec
    with EitherValues
    with SierraRecordGenerators
    with RecordMergerFixtures
    with SourceVHSFixture {

  def assertStored(
    id: TypedSierraRecordNumber,
    transformable: SierraTransformable,
    sourceVHS: SourceVHS[SierraTransformable]
  ): Assertion =
    sourceVHS.underlying
      .getLatest(id.withoutCheckDigit)
      .right
      .get
      .identifiedT shouldBe transformable

  it("creates a record if it receives an item with a bibId that doesn't exist") {
    val sourceVHS = createSourceVHS[SierraTransformable]
    val updater = new Updater[SierraItemRecord](sourceVHS)

    val bibId = createSierraBibNumber
    val newItemRecord = createSierraItemRecordWith(
      bibIds = List(bibId)
    )

    val result = updater.update(newItemRecord)

    result shouldBe a[Right[_, _]]
    result.right.get.map { _.id } shouldBe List(
      Version(bibId.withoutCheckDigit, 0))

    val expectedSierraTransformable =
      createSierraTransformableStubWith(
        bibId = bibId,
        itemRecords = List(newItemRecord)
      )

    assertStored(bibId, expectedSierraTransformable, sourceVHS)
  }

  it("updates an item if it receives an update with a newer date") {
    val bibId = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(bibId)
    )

    val oldTransformable = createSierraTransformableStubWith(
      bibId = bibId,
      itemRecords = List(itemRecord)
    )

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(bibId.withoutCheckDigit, 0) -> oldTransformable
      )
    )
    val updater = new Updater[SierraItemRecord](sourceVHS)

    val newItemRecord = itemRecord.copy(
      data = """{"data": "newer"}""",
      modifiedDate = newerDate
    )
    val expectedTransformable = oldTransformable.copy(
      itemRecords = Map(itemRecord.id -> newItemRecord),
      modifiedTime = newerDate
    )

    val result = updater.update(newItemRecord)

    result shouldBe a[Right[_, _]]
    result.right.get.map { _.id } should contain theSameElementsAs List(
      Version(bibId.withoutCheckDigit, 1))

    assertStored(
      expectedTransformable.sierraId,
      expectedTransformable,
      sourceVHS
    )
  }

  it("unlinks an item if it is updated with an unlinked item") {
    val bibId1 = createSierraBibNumber
    val bibId2 = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(bibId1)
    )

    val sierraTransformable1 = createSierraTransformableStubWith(
      bibId = bibId1,
      itemRecords = List(itemRecord)
    )

    val sierraTransformable2 = createSierraTransformableStubWith(
      bibId = bibId2
    )

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(bibId1.withoutCheckDigit, 0) -> sierraTransformable1,
        Version(bibId2.withoutCheckDigit, 0) -> sierraTransformable2
      )
    )
    val updater = new Updater[SierraItemRecord](sourceVHS)

    val unlinkItemRecord = itemRecord.copy(
      bibIds = List(bibId2),
      unlinkedBibIds = List(bibId1),
      modifiedDate = itemRecord.modifiedDate.plusSeconds(1)
    )

    val expectedTransformable1 = sierraTransformable1.copy(
      itemRecords = Map.empty,
      modifiedTime = unlinkItemRecord.modifiedDate
    )

    val expectedItemRecords = Map(
      itemRecord.id -> itemRecord.copy(
        bibIds = List(bibId2),
        unlinkedBibIds = List(bibId1),
        modifiedDate = unlinkItemRecord.modifiedDate
      )
    )
    val expectedTransformable2 = sierraTransformable2.copy(
      itemRecords = expectedItemRecords,
      modifiedTime = unlinkItemRecord.modifiedDate
    )

    val result = updater.update(unlinkItemRecord)

    result shouldBe a[Right[_, _]]
    result.right.get.map { _.id } should contain theSameElementsAs List(
      Version(bibId1.withoutCheckDigit, 1),
      Version(bibId2.withoutCheckDigit, 1))

    assertStored(bibId1, expectedTransformable1, sourceVHS)
    assertStored(bibId2, expectedTransformable2, sourceVHS)
  }

  it("unlinks and updates a bib from a single call") {
    val bibId1 = createSierraBibNumber
    val bibId2 = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(bibId1, bibId2)
    )

    val sierraTransformable1 = createSierraTransformableStubWith(
      bibId = bibId1,
      itemRecords = List(itemRecord)
    )

    val sierraTransformable2 = createSierraTransformableStubWith(
      bibId = bibId2,
      itemRecords = List(itemRecord)
    )

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(bibId1.withoutCheckDigit, 0) -> sierraTransformable1,
        Version(bibId2.withoutCheckDigit, 0) -> sierraTransformable2
      )
    )
    val updater = new Updater[SierraItemRecord](sourceVHS)

    val unlinkItemRecord = itemRecord.copy(
      bibIds = List(bibId2),
      unlinkedBibIds = List(bibId1),
      modifiedDate = itemRecord.modifiedDate.plusSeconds(1)
    )

    val expectedItemData = Map(
      itemRecord.id -> unlinkItemRecord
    )

    val expectedTransformable1 = sierraTransformable1.copy(
      itemRecords = Map.empty,
      modifiedTime = unlinkItemRecord.modifiedDate
    )

    // In this situation the item was already linked to sierraTransformable2
    // but the modified date is updated in line with the item update
    val expectedTransformable2 = sierraTransformable2.copy(
      itemRecords = expectedItemData,
      modifiedTime = unlinkItemRecord.modifiedDate
    )

    val result = updater.update(unlinkItemRecord)
    result shouldBe a[Right[_, _]]
    result.right.get.map { _.id } should contain theSameElementsAs List(
      Version(bibId1.withoutCheckDigit, 1),
      Version(bibId2.withoutCheckDigit, 1))

    assertStored(bibId1, expectedTransformable1, sourceVHS)
    assertStored(bibId2, expectedTransformable2, sourceVHS)
  }

  it("does not unlink an item if it receives an outdated unlink update") {
    val bibId1 = createSierraBibNumber // linked to the item
    val bibId2 = createSierraBibNumber // not linked to the item

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(bibId1)
    )

    val sierraTransformable1 =
      createSierraTransformableStubWith(
        bibId = bibId1,
        itemRecords = List(itemRecord))

    val sierraTransformable2 =
      createSierraTransformableStubWith(bibId = bibId2)

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(bibId1.withoutCheckDigit, 0) -> sierraTransformable1,
        Version(bibId2.withoutCheckDigit, 0) -> sierraTransformable2
      )
    )
    val updater = new Updater[SierraItemRecord](sourceVHS)

    val unlinkItemRecord = itemRecord.copy(
      bibIds = List(bibId2),
      unlinkedBibIds = List(bibId1),
      modifiedDate = itemRecord.modifiedDate.minusSeconds(1)
    )

    val expectedItemRecords = Map(
      itemRecord.id -> itemRecord.copy(
        bibIds = List(bibId2),
        unlinkedBibIds = List(bibId1),
        modifiedDate = unlinkItemRecord.modifiedDate
      )
    )

    // In this situation the item will _not_ be unlinked from the original
    // record but will be linked to the new record (as this is the first
    // time we've seen the link so it is valid for that bib.
    val expectedTransformable1 = sierraTransformable1
    val expectedTransformable2 = sierraTransformable2.copy(
      itemRecords = expectedItemRecords
    )

    val result = updater.update(unlinkItemRecord)
    result shouldBe a[Right[_, _]]
    result.right.get.map { _.id } should contain theSameElementsAs List(
      Version(bibId2.withoutCheckDigit, 1))

    assertStored(bibId1, expectedTransformable1, sourceVHS)
    assertStored(bibId2, expectedTransformable2, sourceVHS)
  }

  it("does not update an item if it receives an update with an older date") {
    val bibId = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      modifiedDate = newerDate,
      bibIds = List(bibId)
    )

    val transformable = createSierraTransformableStubWith(
      bibId = bibId,
      itemRecords = List(itemRecord)
    )

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(bibId.withoutCheckDigit, 0) -> transformable
      )
    )
    val updater = new Updater[SierraItemRecord](sourceVHS)

    val oldItemRecord = itemRecord.copy(
      modifiedDate = olderDate
    )

    val result = updater.update(oldItemRecord)
    result shouldBe a[Right[_, _]]
    result.right.get shouldBe empty

    assertStored(bibId, transformable, sourceVHS)
  }

  it("re-sends an item if it receives the same update twice") {
    val bibId = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      modifiedDate = newerDate,
      bibIds = List(bibId)
    )

    val sourceVHS = createSourceVHS[SierraTransformable]
    val updater = new Updater[SierraItemRecord](sourceVHS)

    (1 to 5).foreach { _ =>
      val result = updater.update(itemRecord)
      result shouldBe a[Right[_, _]]
      result.value should not be empty
    }

    sourceVHS.underlying.getLatest(bibId.withoutCheckDigit) shouldBe a[Right[_,
                                                                             _]]
  }

  it("adds an item to the record if the bibId exists but has no itemData") {
    val bibId = createSierraBibNumber
    val transformable = createSierraTransformableStubWith(bibId = bibId)

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(bibId.withoutCheckDigit, 0) -> transformable
      )
    )
    val updater = new Updater[SierraItemRecord](sourceVHS)

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(bibId)
    )

    val expectedTransformable = createSierraTransformableStubWith(
      bibId = bibId,
      itemRecords = List(itemRecord)
    )

    val result = updater.update(itemRecord)
    result shouldBe a[Right[_, _]]
    result.right.get.map { _.id } should contain theSameElementsAs List(
      Version(bibId.withoutCheckDigit, 1))

    assertStored(bibId, expectedTransformable, sourceVHS)
  }

  class BrokenStore
      extends HybridStoreWithMaxima[
        String,
        Int,
        S3ObjectLocation,
        SierraTransformable] {
    override protected def createTypeStoreId(
      id: Version[String, Int]): S3ObjectLocation =
      createS3ObjectLocation

    implicit override val indexedStore
      : Store[Version[String, Int], S3ObjectLocation] with Maxima[
        String,
        Version[String, Int],
        S3ObjectLocation] =
      new MemoryStore[Version[String, Int], S3ObjectLocation](
        initialEntries = Map.empty) with MemoryMaxima[String, S3ObjectLocation]

    override implicit val typedStore
      : TypedStore[S3ObjectLocation, SierraTransformable] =
      MemoryTypedStore[S3ObjectLocation, SierraTransformable]()
  }

  it("fails if merging an item fails") {
    val exception = new RuntimeException("AAAAARGH!")

    val sourceVHS = new SourceVHS[SierraTransformable](
      underlying = new VersionedHybridStore(
        hybridStore = new BrokenStore()
      ) {
        override def upsert(id: String)(t: SierraTransformable)(
          f: UpdateFunction): UpdateEither =
          Left(UpdateWriteError(StoreWriteError(exception)))
      }
    )

    val brokenUpdater = new Updater[SierraItemRecord](sourceVHS)

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(createSierraBibNumber)
    )
    brokenUpdater.update(itemRecord).left.get.e shouldBe exception
  }

  it("returns a failed future if unlinking an item fails") {
    val exception = new RuntimeException("AAAAARGH!")

    val sourceVHS = new SourceVHS[SierraTransformable](
      underlying = new VersionedHybridStore(
        hybridStore = new BrokenStore()
      ) {
        override def update(id: String)(f: UpdateFunction): UpdateEither =
          Left(UpdateWriteError(StoreWriteError(exception)))
      }
    )

    val brokenUpdater = new Updater[SierraItemRecord](sourceVHS)

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(createSierraBibNumber)
    )
    brokenUpdater.update(itemRecord).left.get.e shouldBe exception
  }
}
