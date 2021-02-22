package weco.catalogue.sierra_adapter.linker

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.sierra_adapter.model.SierraGenerators

class LinkingRecordTest
  extends AnyFunSpec
    with Matchers
    with SierraGenerators {

  it("combines the bibIds in the final result") {
    val bibIds = createSierraBibNumbers(count = 5)
    val existingRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = bibIds.slice(0, 3)
    )
    val newRecord = createSierraItemRecordWith(
      id = existingRecord.id,
      modifiedDate = newerDate,
      bibIds = bibIds
    )

    val link = LinkingRecord(existingRecord).update(newRecord).get

    link.bibIds shouldBe bibIds
    link.unlinkedBibIds shouldBe List()
  }

  it("records unlinked bibIds") {
    val bibIds = createSierraBibNumbers(count = 5)

    val existingRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = bibIds.slice(0, 3)
    )
    val newRecord = createSierraItemRecordWith(
      id = existingRecord.id,
      modifiedDate = newerDate,
      bibIds = bibIds.slice(2, 5)
    )

    val link =
      LinkingRecord(existingRecord).update(newRecord).get

    link.bibIds shouldBe bibIds.slice(2, 5)
    link.unlinkedBibIds shouldBe bibIds.slice(0, 2)
  }

  it("preserves existing unlinked bibIds") {
    val bibIds = createSierraBibNumbers(count = 5)

    val existingRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = bibIds.slice(0, 3),
      unlinkedBibIds = bibIds.slice(3, 5)
    )
    val newRecord = createSierraItemRecordWith(
      id = existingRecord.id,
      modifiedDate = newerDate,
      bibIds = existingRecord.bibIds
    )

    val link = LinkingRecord(existingRecord).update(newRecord).get
    link.unlinkedBibIds should contain theSameElementsAs existingRecord.unlinkedBibIds
  }

  it("does not duplicate unlinked bibIds") {
    // This would be an unusual scenario to arise, but check we handle it anyway!
    val bibIds = createSierraBibNumbers(count = 3)

    val existingRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = bibIds,
      unlinkedBibIds = List(bibIds(2))
    )
    val newRecord = createSierraItemRecordWith(
      id = existingRecord.id,
      modifiedDate = newerDate,
      bibIds = bibIds.slice(0, 2)
    )

    val link = LinkingRecord(existingRecord).update(newRecord).get
    link.bibIds shouldBe bibIds.slice(0, 2)
    link.unlinkedBibIds shouldBe List(bibIds(2))
  }

  it("removes an unlinked bibId if it appears on a new record") {
    val bibIds = createSierraBibNumbers(count = 3)

    val existingRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = List(),
      unlinkedBibIds = bibIds
    )
    val newRecord = createSierraItemRecordWith(
      id = existingRecord.id,
      modifiedDate = newerDate,
      bibIds = bibIds.slice(0, 2)
    )

    val link = LinkingRecord(existingRecord).update(newRecord).get
    link.bibIds shouldBe bibIds.slice(0, 2)
    link.unlinkedBibIds shouldBe List(bibIds(2))
  }

  it(
    "returns the link if it has the same modified date as the one already stored") {
    val record = createSierraItemRecord
    val link = LinkingRecord(record)

    link.update(record) shouldBe Some(link)
  }

  it("returns None if it receives an outdated update") {
    val bibIds = createSierraBibNumbers(count = 5)

    val oldRecord = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = bibIds.slice(0, 3)
    )

    val newRecord = createSierraItemRecordWith(
      id = oldRecord.id,
      modifiedDate = newerDate,
      bibIds = bibIds
    )
    val newLink = LinkingRecord(newRecord)

    newLink.update(oldRecord) shouldBe None
  }
}

