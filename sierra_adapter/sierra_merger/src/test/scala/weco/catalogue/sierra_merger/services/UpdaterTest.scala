package weco.catalogue.sierra_merger.services

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import uk.ac.wellcome.sierra_adapter.model.{
  SierraGenerators,
  SierraItemRecord,
  SierraTransformable
}
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
import weco.catalogue.sierra_merger.fixtures.RecordMergerFixtures
import weco.catalogue.source_model.fixtures.SourceVHSFixture
import weco.catalogue.source_model.store.SourceVHS

class UpdaterTest
    extends AnyFunSpec
    with EitherValues
    with SierraGenerators
    with RecordMergerFixtures
    with SourceVHSFixture {

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
      createSierraTransformableWith(
        sierraId = bibId,
        maybeBibRecord = None,
        itemRecords = List(newItemRecord)
      )

    assertStored(
      bibId.withoutCheckDigit,
      expectedSierraTransformable,
      sourceVHS)
  }

  it("updates an item if it receives an update with a newer date") {
    val bibId = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(bibId)
    )

    val oldTransformable = createSierraTransformableWith(
      sierraId = bibId,
      maybeBibRecord = None,
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
      itemRecords = Map(itemRecord.id -> newItemRecord)
    )

    val result = updater.update(newItemRecord)

    result shouldBe a[Right[_, _]]
    result.right.get.map { _.id } should contain theSameElementsAs (List(
      Version(bibId.withoutCheckDigit, 1)))

    assertStored(
      expectedTransformable.sierraId.withoutCheckDigit,
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

    val sierraTransformable1 = createSierraTransformableWith(
      sierraId = bibId1,
      maybeBibRecord = None,
      itemRecords = List(itemRecord)
    )

    val sierraTransformable2 = createSierraTransformableWith(
      sierraId = bibId2,
      maybeBibRecord = None
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
      itemRecords = Map.empty
    )

    val expectedItemRecords = Map(
      itemRecord.id -> itemRecord.copy(
        bibIds = List(bibId2),
        unlinkedBibIds = List(bibId1),
        modifiedDate = unlinkItemRecord.modifiedDate
      )
    )
    val expectedTransformable2 = sierraTransformable2.copy(
      itemRecords = expectedItemRecords
    )

    val result = updater.update(unlinkItemRecord)

    result shouldBe a[Right[_, _]]
    result.right.get.map { _.id } should contain theSameElementsAs (List(
      Version(bibId1.withoutCheckDigit, 1),
      Version(bibId2.withoutCheckDigit, 1)))

    assertStored(
      bibId1.withoutCheckDigit,
      expectedTransformable1,
      sourceVHS
    )
    assertStored(
      bibId2.withoutCheckDigit,
      expectedTransformable2,
      sourceVHS
    )
  }

  it("unlinks and updates a bib from a single call") {
    val bibId1 = createSierraBibNumber
    val bibId2 = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(bibId1)
    )

    val sierraTransformable1 = createSierraTransformableWith(
      sierraId = bibId1,
      maybeBibRecord = None,
      itemRecords = List(itemRecord)
    )

    val sierraTransformable2 = createSierraTransformableWith(
      sierraId = bibId2,
      maybeBibRecord = None,
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
      itemRecords = Map.empty
    )

    // In this situation the item was already linked to sierraTransformable2
    // but the modified date is updated in line with the item update
    val expectedTransformable2 = sierraTransformable2.copy(
      itemRecords = expectedItemData
    )

    val result = updater.update(unlinkItemRecord)
    result shouldBe a[Right[_, _]]
    result.right.get.map { _.id } should contain theSameElementsAs (List(
      Version(bibId1.withoutCheckDigit, 1),
      Version(bibId2.withoutCheckDigit, 1)))
    assertStored(
      bibId1.withoutCheckDigit,
      expectedTransformable1,
      sourceVHS
    )
    assertStored(
      bibId2.withoutCheckDigit,
      expectedTransformable2,
      sourceVHS
    )
  }

  it("does not unlink an item if it receives an outdated unlink update") {
    val bibId1 = createSierraBibNumber // linked to the item
    val bibId2 = createSierraBibNumber // not linked to the item

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(bibId1)
    )

    val sierraTransformable1 =
      createSierraTransformableWith(
        sierraId = bibId1,
        itemRecords = List(itemRecord))

    val sierraTransformable2 =
      createSierraTransformableWith(sierraId = bibId2)

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

    assertStored(
      bibId1.withoutCheckDigit,
      expectedTransformable1,
      sourceVHS
    )
    assertStored(
      bibId2.withoutCheckDigit,
      expectedTransformable2,
      sourceVHS
    )
  }

  it("does not update an item if it receives an update with an older date") {
    val bibId = createSierraBibNumber

    val itemRecord = createSierraItemRecordWith(
      modifiedDate = newerDate,
      bibIds = List(bibId)
    )

    val transformable = createSierraTransformableWith(
      sierraId = bibId,
      maybeBibRecord = None,
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

    assertStored(
      bibId.withoutCheckDigit,
      transformable,
      sourceVHS
    )
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
    val transformable = createSierraTransformableWith(
      sierraId = bibId,
      maybeBibRecord = None
    )

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(bibId.withoutCheckDigit, 0) -> transformable
      )
    )
    val updater = new Updater[SierraItemRecord](sourceVHS)

    val itemRecord = createSierraItemRecordWith(
      bibIds = List(bibId)
    )

    val expectedTransformable = createSierraTransformableWith(
      sierraId = bibId,
      maybeBibRecord = None,
      itemRecords = List(itemRecord)
    )

    val result = updater.update(itemRecord)
    result shouldBe a[Right[_, _]]
    result.right.get.map { _.id } should contain theSameElementsAs (List(
      Version(bibId.withoutCheckDigit, 1)))
    assertStored(
      bibId.withoutCheckDigit,
      expectedTransformable,
      sourceVHS
    )
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
