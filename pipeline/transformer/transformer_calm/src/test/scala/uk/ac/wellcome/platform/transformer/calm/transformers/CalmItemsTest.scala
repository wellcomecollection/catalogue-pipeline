package uk.ac.wellcome.platform.transformer.calm.transformers

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.{
  AccessCondition,
  IdState,
  Item,
  UnknownAccessStatus
}
import weco.catalogue.source_model.generators.CalmRecordGenerators

class CalmItemsTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with CalmRecordGenerators {
  it("returns a Left[UnknownAccessStatus] if it can't parse the AccessStatus") {
    val record = createCalmRecordWith(
      "AccessStatus" -> "Unknown???"
    )

    CalmItems(record).left.value shouldBe a[UnknownAccessStatus]
  }

  describe("sets access conditions correctly") {
    it("doesn't set any access conditions if there's nothing useful to show") {
      val record = createCalmRecord

      val items = CalmItems(record).value

      getAccessConditions(items) shouldBe empty
    }

    it("uses ClosedUntil if the access status is Closed") {
      val record = createCalmRecordWith(
        "AccessStatus" -> "Closed",
        "ClosedUntil" -> "2002/02/02"
      )

      val items = CalmItems(record).value

      val accessConditions = getAccessConditions(items)

      accessConditions should have size 1
      accessConditions.head.to shouldBe Some("2002/02/02")
    }

    it("ignores ClosedUntil if the access status is not Closed") {
      val record = createCalmRecordWith(
        "AccessStatus" -> "Open",
        "ClosedUntil" -> "2002/02/02"
      )

      val items = CalmItems(record).value

      val accessConditions = getAccessConditions(items)

      accessConditions should have size 1
      accessConditions.head.to shouldBe None
    }

    it("uses UserDate1 if the access status is Restricted") {
      val record = createCalmRecordWith(
        "AccessStatus" -> "Restricted",
        "UserDate1" -> "2002/02/02"
      )

      val items = CalmItems(record).value

      val accessConditions = getAccessConditions(items)

      accessConditions should have size 1
      accessConditions.head.to shouldBe Some("2002/02/02")
    }

    it("ignores UserDate1 if the access status is not Restricted") {
      val record = createCalmRecordWith(
        "AccessStatus" -> "Open",
        "UserDate1" -> "2002/02/02"
      )

      val items = CalmItems(record).value

      val accessConditions = getAccessConditions(items)

      accessConditions should have size 1
      accessConditions.head.to shouldBe None
    }

    def getAccessConditions(
      items: Seq[Item[IdState.Unminted]]): List[AccessCondition] = {
      items should have size 1

      val locations = items.head.locations
      locations should have size 1

      locations.head.accessConditions
    }
  }
}
