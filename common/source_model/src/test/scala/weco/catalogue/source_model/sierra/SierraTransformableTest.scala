package weco.catalogue.source_model.sierra

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.sierra.models.identifiers.SierraItemNumber

import java.time.Instant

class SierraTransformableTest
    extends AnyFunSpec
    with Matchers
    with SierraRecordGenerators {

  it("allows creation of SierraTransformable with no data") {
    // We need this for the case where:
    //
    //  1. We receive an item that links to a bib record b123
    //  2. Later, we receive an update to the item that unlinks it
    //
    // Then we'd have a SierraTransformable for b123 but no attached records.
    // Is that likely?  Hard to say, but we can handle it so we might as well.
    SierraTransformable(
      sierraId = createSierraBibNumber,
      modifiedTime = Instant.now
    )
  }

  it("allows creation from only a SierraBibRecord") {
    val bibRecord = createSierraBibRecord
    val mergedRecord = SierraTransformable(bibRecord = bibRecord)
    mergedRecord.sierraId shouldEqual bibRecord.id
    mergedRecord.maybeBibRecord.get shouldEqual bibRecord
  }

  it("allows looking up items by ID") {
    val bibId = createSierraBibNumber
    val itemRecords = (0 to 3).map {
      _ =>
        createSierraItemRecordWith(bibIds = List(bibId))
    }
    val transformable = createSierraTransformableStubWith(
      bibId = bibId,
      itemRecords = itemRecords
    )

    transformable.itemRecords(itemRecords.head.id) shouldBe itemRecords.head

    // The first one should work by identity (the ID is the same object
    // as the key).  Check it also works with a record number which is equal
    // but not identical.
    val recordNumber = SierraItemNumber(itemRecords.head.id.withoutCheckDigit)
    transformable.itemRecords(recordNumber) shouldBe itemRecords.head
  }
}
