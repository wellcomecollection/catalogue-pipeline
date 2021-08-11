package weco.pipeline.sierra_linker.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.catalogue.source_model.sierra.SierraItemRecord

class LinkOpsTest extends AnyFunSpec with Matchers with SierraRecordGenerators {

  val linkOps: LinkOps[SierraItemRecord] = LinkOps.itemLinksOps

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

    val mergedRecord =
      linkOps
        .updateLink(existingLink = Link(existingRecord), newRecord = newRecord)
        .get
    mergedRecord.bibIds shouldBe bibIds
    mergedRecord.unlinkedBibIds shouldBe List()
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

    val mergedRecord =
      linkOps
        .updateLink(existingLink = Link(existingRecord), newRecord = newRecord)
        .get
    mergedRecord.bibIds shouldBe bibIds.slice(2, 5)
    mergedRecord.unlinkedBibIds shouldBe bibIds.slice(0, 2)
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

    val mergedRecord =
      linkOps
        .updateLink(existingLink = Link(existingRecord), newRecord = newRecord)
        .get
    mergedRecord.unlinkedBibIds should contain theSameElementsAs existingRecord.unlinkedBibIds
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

    val mergedRecord =
      linkOps
        .updateLink(existingLink = Link(existingRecord), newRecord = newRecord)
        .get
    mergedRecord.bibIds shouldBe bibIds.slice(0, 2)
    mergedRecord.unlinkedBibIds shouldBe List(bibIds(2))
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

    val mergedRecord =
      linkOps
        .updateLink(existingLink = Link(existingRecord), newRecord = newRecord)
        .get
    mergedRecord.bibIds shouldBe bibIds.slice(0, 2)
    mergedRecord.unlinkedBibIds shouldBe List(bibIds(2))
  }

  it(
    "returns the link if it has the same modified date as the one already stored") {
    val record = createSierraItemRecord
    val link = Link(record)

    linkOps.updateLink(link, record) shouldBe Some(link)
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
    val newLink = Link(newRecord)

    linkOps.updateLink(newLink, oldRecord) shouldBe None
  }
}
