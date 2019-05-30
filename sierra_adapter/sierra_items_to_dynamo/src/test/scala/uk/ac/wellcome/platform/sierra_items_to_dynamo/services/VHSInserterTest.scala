package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import org.scalatest.{Assertion, FunSpec, Matchers, TryValues}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage._
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.storage.vhs.EmptyMetadata

class VHSInserterTest
    extends FunSpec
    with Matchers
    with SierraAdapterHelpers
    with TryValues
    with SierraGenerators {

  it("inserts an ItemRecord into the VHS") {
    val vhs = createItemVhs()
    val vhsInserter = new VHSInserter(vhs)

    val itemRecord = createSierraItemRecord

    vhsInserter.insertIntoVhs(itemRecord) shouldBe a[Right[_, _]]

    assertStored(itemRecord, vhs = vhs)
  }

  it("does not overwrite new data with old data") {
    val vhs = createItemVhs()
    val vhsInserter = new VHSInserter(vhs)

    val newRecord = createSierraItemRecordWith(
      modifiedDate = newerDate,
      bibIds = List(createSierraBibNumber)
    )
    storeSingleRecord(newRecord, vhs = vhs)

    val oldRecord = createSierraItemRecordWith(
      id = newRecord.id,
      modifiedDate = olderDate,
      bibIds = List(createSierraBibNumber)
    )

    vhsInserter.insertIntoVhs(oldRecord) shouldBe a[Right[_, _]]
    assertStored(newRecord, vhs = vhs)
  }

  it("overwrites old data with new data") {
    val vhs = createItemVhs()
    val vhsInserter = new VHSInserter(vhs)

    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(createSierraBibNumber)
    )
    storeSingleRecord(oldRecord, vhs = vhs)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = oldRecord.bibIds ++ List(createSierraBibNumber)
    )

    vhsInserter.insertIntoVhs(newRecord) shouldBe a[Right[_, _]]

    assertStored(newRecord, vhs = vhs)
  }

  it("records unlinked bibIds") {
    val vhs = createItemVhs()
    val vhsInserter = new VHSInserter(vhs)

    val bibIds = createSierraBibNumbers(count = 3)

    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = bibIds
    )
    storeSingleRecord(oldRecord, vhs = vhs)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = List(bibIds(0), bibIds(1))
    )

    vhsInserter.insertIntoVhs(newRecord) shouldBe a[Right[_, _]]

    assertStored(
      newRecord.copy(unlinkedBibIds = List(bibIds(2))),
      vhs = vhs)
  }

  it("adds new bibIds and records unlinked bibIds in the same update") {
    val vhs = createItemVhs()
    val vhsInserter = new VHSInserter(vhs)

    val bibIds = createSierraBibNumbers(count = 4)

    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(bibIds(0), bibIds(1), bibIds(2))
    )
    storeSingleRecord(oldRecord, vhs = vhs)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = List(bibIds(1), bibIds(2), bibIds(3))
    )

    vhsInserter.insertIntoVhs(newRecord) shouldBe a[Right[_, _]]

    assertStored(
      newRecord.copy(unlinkedBibIds = List(bibIds(0))),
      vhs = vhs)
  }

  it("preserves existing unlinked bibIds in DynamoDB") {
    val vhs = createItemVhs()
    val vhsInserter = new VHSInserter(vhs)

    val bibIds = createSierraBibNumbers(count = 5)

    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(bibIds(0), bibIds(1), bibIds(2)),
      unlinkedBibIds = List(bibIds(4))
    )
    storeSingleRecord(oldRecord, vhs = vhs)

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = List(bibIds(1), bibIds(2), bibIds(3)),
      unlinkedBibIds = List()
    )

    vhsInserter.insertIntoVhs(newRecord) shouldBe a[Right[_, _]]

    val storedRecord = vhs.get(id = oldRecord.id.withoutCheckDigit).right.value
    storedRecord.unlinkedBibIds shouldBe List(bibIds(4), bibIds(0))
  }

  it("fails if the VHS returns an error when updating an item") {
    val record = createSierraItemRecord
    val exception = new Throwable("BOOM!")

    val brokenStore = new SierraItemStore() {
      override def put(namespace: String)(
        input: SierraItemRecord,
        keyPrefix: KeyPrefix,
        keySuffix: KeySuffix,
        userMetadata: Map[String, String]): Either[WriteError, ObjectLocation] =
        Left(BackendWriteError(exception))
    }

    val vhs = createItemVhs(store = brokenStore)
    val vhsInserter = new VHSInserter(vhs)

    vhsInserter.insertIntoVhs(record).left.value.e shouldBe exception
  }

  def storeSingleRecord(
    itemRecord: SierraItemRecord,
    vhs: SierraItemVHS
  ): Assertion = {
    val result =
      vhs.update(id = itemRecord.id.withoutCheckDigit)(
        ifNotExisting = (itemRecord, EmptyMetadata())
      )(
        ifExisting = (existingRecord, existingMetadata) =>
          throw new RuntimeException(
            s"VHS should be empty; got ($existingRecord, $existingMetadata)!")
      )

    result shouldBe a[Right[_, _]]
  }
}
