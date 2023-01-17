package weco.pipeline.transformer.calm.transformers

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus
}
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.generators.CalmRecordGenerators

class CalmItemsTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with CalmRecordGenerators {
  describe("sets access conditions correctly") {
    it("doesn't set any access conditions if there's nothing useful to show") {
      val record = createCalmRecord

      val items = CalmItems(record)

      getAccessConditions(items) shouldBe empty
    }

    it("sets access conditions based on the access status") {
      val record = createCalmRecordWith(
        "AccessStatus" -> "Open",
        "AccessConditions" -> "The papers are available subject to the usual conditions of access to Archives and Manuscripts material."
      )

      val items = CalmItems(record)

      getAccessConditions(items) shouldBe List(
        AccessCondition(
          method = AccessMethod.NotRequestable,
          status = AccessStatus.Open
        )
      )
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
