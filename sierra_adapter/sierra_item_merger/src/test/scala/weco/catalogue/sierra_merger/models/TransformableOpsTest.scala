package weco.catalogue.sierra_merger.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.sierra_adapter.model.{
  SierraGenerators,
  SierraTransformable
}

class TransformableOpsTest
    extends AnyFunSpec
    with Matchers
    with SierraGenerators {
  import weco.catalogue.sierra_merger.models.TransformableOps._

  describe("bibTransformableOps") {
    describe("add") {
      it("merges data from a bibRecord when empty") {
        val bibRecord = createSierraBibRecord
        val transformable = createSierraTransformableWith(
          sierraId = bibRecord.id,
          maybeBibRecord = None
        )

        val newTransformable = transformable.add(bibRecord)
        newTransformable.get.maybeBibRecord.get shouldEqual bibRecord
      }

      it("only merges bib records with matching ids") {
        val bibRecord = createSierraBibRecord
        val transformable = createSierraTransformable

        val caught = intercept[RuntimeException] {
          transformable.add(bibRecord)
        }
        caught.getMessage shouldEqual s"Non-matching bib ids ${bibRecord.id} != ${transformable.sierraId}"
      }

      it("returns the same transformable if you merge the same record more than once") {
        val bibRecord = createSierraBibRecord

        val transformable = SierraTransformable(bibRecord)

        transformable.add(bibRecord) shouldBe Some(transformable)
      }

      it("returns None when merging a stale update") {
        val oldBibRecord = createSierraBibRecordWith(
          modifiedDate = olderDate
        )

        val newBibRecord = createSierraBibRecordWith(
          id = oldBibRecord.id,
          modifiedDate = newerDate
        )

        val transformable = SierraTransformable(
          bibRecord = newBibRecord
        )

        transformable.add(oldBibRecord) shouldBe None
      }

      it("updates bibData when merging bib records with newer data") {
        val newBibRecord = createSierraBibRecordWith(
          modifiedDate = newerDate
        )

        val oldBibRecord = createSierraBibRecordWith(
          id = newBibRecord.id,
          modifiedDate = olderDate
        )

        val transformable = SierraTransformable(
          bibRecord = oldBibRecord
        )

        transformable.add(newBibRecord).get.maybeBibRecord shouldBe Some(
          newBibRecord)
      }
    }

    describe("remove") {
      it("throws an error if you try to remove a SierraBibRecord from a SierraTransformable") {
        val bibRecord = createSierraBibRecord
        val transformable = createSierraTransformable

        val caught = intercept[RuntimeException] {
          transformable.remove(bibRecord)
        }
        caught.getMessage shouldEqual s"We should never be removing a bib record from a SierraTransformable (${transformable.sierraId})"
      }
    }
  }

  describe("itemTransformableOps") {
    describe("add") {
      it("adds the item if it doesn't exist already") {
        val bibId = createSierraBibNumber
        val record = createSierraItemRecordWith(
          bibIds = List(bibId)
        )

        val sierraTransformable =
          createSierraTransformableWith(sierraId = bibId)
        val result = sierraTransformable.add(record)

        result.get.itemRecords shouldBe Map(record.id -> record)
      }

      it("updates itemData when merging item records with newer data") {
        val bibId = createSierraBibNumber
        val itemRecord = createSierraItemRecordWith(
          modifiedDate = olderDate,
          bibIds = List(bibId)
        )

        val sierraTransformable = createSierraTransformableWith(
          sierraId = bibId,
          itemRecords = List(itemRecord)
        )

        val newerRecord = itemRecord.copy(
          data = """{"hey": "some new data"}""",
          modifiedDate = newerDate,
          bibIds = List(bibId)
        )
        val result = sierraTransformable.add(newerRecord)

        result.get shouldBe sierraTransformable.copy(
          itemRecords = Map(itemRecord.id -> newerRecord))
      }

      it("returns the record if you apply the same update more than once") {
        val bibId = createSierraBibNumber
        val record = createSierraItemRecordWith(
          bibIds = List(bibId)
        )

        val sierraTransformable =
          createSierraTransformableWith(sierraId = bibId)

        val transformable1 = sierraTransformable.add(record)
        val transformable2 = transformable1.get.add(record)

        transformable2 shouldBe transformable1
      }

      it("returns None when merging item records with stale data") {
        val bibId = createSierraBibNumber
        val itemRecord = createSierraItemRecordWith(
          modifiedDate = newerDate,
          bibIds = List(bibId)
        )

        val sierraTransformable = createSierraTransformableWith(
          sierraId = bibId,
          itemRecords = List(itemRecord)
        )

        val oldRecord = itemRecord.copy(
          modifiedDate = olderDate,
          data = """{"older": "data goes here"}"""
        )
        val result = sierraTransformable.add(oldRecord)
        result shouldBe None
      }

      it("supports adding multiple items to a merged record") {
        val bibId = createSierraBibNumber
        val record1 = createSierraItemRecordWith(
          bibIds = List(bibId)
        )
        val record2 = createSierraItemRecordWith(
          bibIds = List(bibId)
        )

        val sierraTransformable =
          createSierraTransformableWith(sierraId = bibId)
        val result1 = sierraTransformable.add(record1)
        val result2 = result1.get.add(record2)

        result1.get.itemRecords(record1.id) shouldBe record1
        result2.get.itemRecords(record2.id) shouldBe record2
      }

      it("only merges item records with matching bib IDs") {
        val bibId = createSierraBibNumber
        val unrelatedBibId = createSierraBibNumber

        val record = createSierraItemRecordWith(
          bibIds = List(unrelatedBibId),
          unlinkedBibIds = List()
        )

        val sierraTransformable =
          createSierraTransformableWith(sierraId = bibId)

        val caught = intercept[RuntimeException] {
          sierraTransformable.add(record)
        }

        caught.getMessage shouldEqual s"Non-matching bib id $bibId in item bib List($unrelatedBibId)"
      }
    }

    describe("remove") {
      it("removes the item if it already exists") {
        val bibId = createSierraBibNumber

        val record = createSierraItemRecordWith(
          bibIds = List(bibId),
          unlinkedBibIds = List()
        )

        val unlinkedItemRecord = createSierraItemRecordWith(
          id = record.id,
          bibIds = List(),
          modifiedDate = record.modifiedDate.plusSeconds(1),
          unlinkedBibIds = List(bibId)
        )

        val sierraTransformable = createSierraTransformableWith(
          sierraId = bibId,
          itemRecords = List(record)
        )

        val expectedSierraTransformable = sierraTransformable.copy(
          itemRecords = Map.empty
        )

        sierraTransformable
          .remove(unlinkedItemRecord)
          .get shouldBe expectedSierraTransformable
      }

      it("returns None when merging an unlinked record which is already absent") {
        val bibId = createSierraBibNumber

        val record = createSierraItemRecordWith(
          bibIds = List(bibId),
          unlinkedBibIds = List()
        )

        val previouslyUnlinkedRecord = createSierraItemRecordWith(
          bibIds = List(),
          unlinkedBibIds = List(bibId)
        )

        val sierraTransformable = createSierraTransformableWith(
          sierraId = bibId,
          itemRecords = List(record)
        )

        sierraTransformable.remove(previouslyUnlinkedRecord) shouldBe None
      }

      it("returns None when merging an unlinked record which has linked more recently") {
        val bibId = createSierraBibNumber

        val record = createSierraItemRecordWith(
          modifiedDate = newerDate,
          bibIds = List(bibId),
          unlinkedBibIds = List()
        )

        val outOfDateUnlinkedRecord = record.copy(
          modifiedDate = olderDate,
          bibIds = List(),
          unlinkedBibIds = List(bibId)
        )

        val sierraTransformable = createSierraTransformableWith(
          sierraId = bibId,
          itemRecords = List(record)
        )

        sierraTransformable.remove(outOfDateUnlinkedRecord) shouldBe None
      }

      it("only unlinks item records with matching bib IDs") {
        val bibId = createSierraBibNumber
        val unrelatedBibId = createSierraBibNumber

        val record = createSierraItemRecordWith(
          bibIds = List(bibId),
          unlinkedBibIds = List()
        )

        val unrelatedItemRecord = createSierraItemRecordWith(
          bibIds = List(),
          unlinkedBibIds = List(unrelatedBibId)
        )

        val sierraTransformable = createSierraTransformableWith(
          sierraId = bibId,
          itemRecords = List(record)
        )

        val caught = intercept[RuntimeException] {
          sierraTransformable.remove(unrelatedItemRecord)
        }

        caught.getMessage shouldEqual s"Non-matching bib id $bibId in item unlink bibs List($unrelatedBibId)"
      }
    }
  }

  describe("holdingsTransformableOps") {
    describe("add") {
      it("adds the holdings if it doesn't exist already") {
        val bibId = createSierraBibNumber
        val record = createSierraHoldingsRecordWith(
          bibIds = List(bibId)
        )

        val sierraTransformable =
          createSierraTransformableWith(sierraId = bibId)
        val result = sierraTransformable.add(record)

        result.get.holdingsRecords shouldBe Map(record.id -> record)
      }

      it("updates the holdings when merging records with newer data") {
        val bibId = createSierraBibNumber
        val olderRecord = createSierraHoldingsRecordWith(
          modifiedDate = olderDate,
          bibIds = List(bibId)
        )

        val sierraTransformable = createSierraTransformableWith(
          sierraId = bibId,
          holdingsRecords = List(olderRecord)
        )

        val newerRecord = olderRecord.copy(
          data = """{"hey": "some new data"}""",
          modifiedDate = newerDate,
          bibIds = List(bibId)
        )
        val result = sierraTransformable.add(newerRecord)

        result.get shouldBe sierraTransformable.copy(
          holdingsRecords = Map(olderRecord.id -> newerRecord))
      }

      it("returns the same record if you apply the same update more than once") {
        val bibId = createSierraBibNumber
        val record = createSierraHoldingsRecordWith(
          bibIds = List(bibId)
        )

        val sierraTransformable =
          createSierraTransformableWith(sierraId = bibId)

        val transformable1 = sierraTransformable.add(record)
        val transformable2 = transformable1.get.add(record)

        transformable2 shouldBe transformable1
      }

      it("returns None when merging item records with stale data") {
        val bibId = createSierraBibNumber
        val newerRecord = createSierraHoldingsRecordWith(
          modifiedDate = newerDate,
          bibIds = List(bibId)
        )

        val sierraTransformable = createSierraTransformableWith(
          sierraId = bibId,
          holdingsRecords = List(newerRecord)
        )

        val oldRecord = newerRecord.copy(
          modifiedDate = olderDate,
          data = """{"older": "data goes here"}"""
        )
        val result = sierraTransformable.add(oldRecord)
        result shouldBe None
      }

      it("supports adding multiple holdings to a merged record") {
        val bibId = createSierraBibNumber
        val record1 = createSierraHoldingsRecordWith(
          bibIds = List(bibId)
        )
        val record2 = createSierraHoldingsRecordWith(
          bibIds = List(bibId)
        )

        val sierraTransformable =
          createSierraTransformableWith(sierraId = bibId)
        val result1 = sierraTransformable.add(record1)
        val result2 = result1.get.add(record2)

        result1.get.holdingsRecords(record1.id) shouldBe record1
        result2.get.holdingsRecords(record2.id) shouldBe record2
      }

      it("only merges item records with matching bib IDs") {
        val bibId = createSierraBibNumber
        val unrelatedBibId = createSierraBibNumber

        val record = createSierraHoldingsRecordWith(
          bibIds = List(unrelatedBibId),
          unlinkedBibIds = List()
        )

        val sierraTransformable =
          createSierraTransformableWith(sierraId = bibId)

        val caught = intercept[RuntimeException] {
          sierraTransformable.add(record)
        }

        caught.getMessage shouldEqual s"Non-matching bib id $bibId in holdings bib List($unrelatedBibId)"
      }
    }

    describe("remove") {
      it("removes the holdings if it already exists") {
        val bibId = createSierraBibNumber

        val record = createSierraHoldingsRecordWith(
          bibIds = List(bibId),
          unlinkedBibIds = List()
        )

        val unlinkedRecord = createSierraHoldingsRecordWith(
          id = record.id,
          bibIds = List(),
          modifiedDate = record.modifiedDate.plusSeconds(1),
          unlinkedBibIds = List(bibId)
        )

        val sierraTransformable = createSierraTransformableWith(
          sierraId = bibId,
          holdingsRecords = List(record)
        )

        val expectedSierraTransformable = sierraTransformable.copy(
          holdingsRecords = Map.empty
        )

        sierraTransformable
          .remove(unlinkedRecord)
          .get shouldBe expectedSierraTransformable
      }

      it("returns None when merging an unlinked record which is already absent") {
        val bibId = createSierraBibNumber

        val record = createSierraHoldingsRecordWith(
          bibIds = List(bibId),
          unlinkedBibIds = List()
        )

        val previouslyUnlinkedRecord = createSierraHoldingsRecordWith(
          bibIds = List(),
          unlinkedBibIds = List(bibId)
        )

        val sierraTransformable = createSierraTransformableWith(
          sierraId = bibId,
          holdingsRecords = List(record)
        )

        sierraTransformable.remove(previouslyUnlinkedRecord) shouldBe None
      }

      it("returns None when merging an unlinked record which has linked more recently") {
        val bibId = createSierraBibNumber

        val record = createSierraHoldingsRecordWith(
          modifiedDate = newerDate,
          bibIds = List(bibId),
          unlinkedBibIds = List()
        )

        val outOfDateUnlinkedRecord = record.copy(
          modifiedDate = olderDate,
          bibIds = List(),
          unlinkedBibIds = List(bibId)
        )

        val sierraTransformable = createSierraTransformableWith(
          sierraId = bibId,
          holdingsRecords = List(record)
        )

        sierraTransformable.remove(outOfDateUnlinkedRecord) shouldBe None
      }

      it("only unlinks holdings records with matching bib IDs") {
        val bibId = createSierraBibNumber
        val unrelatedBibId = createSierraBibNumber

        val record = createSierraHoldingsRecordWith(
          bibIds = List(bibId),
          unlinkedBibIds = List()
        )

        val unrelatedItemRecord = createSierraHoldingsRecordWith(
          bibIds = List(),
          unlinkedBibIds = List(unrelatedBibId)
        )

        val sierraTransformable = createSierraTransformableWith(
          sierraId = bibId,
          holdingsRecords = List(record)
        )

        val caught = intercept[RuntimeException] {
          sierraTransformable.remove(unrelatedItemRecord)
        }

        caught.getMessage shouldEqual s"Non-matching bib id $bibId in holdings unlink bibs List($unrelatedBibId)"
      }
    }
  }
}
