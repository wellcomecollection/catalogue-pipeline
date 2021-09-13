package weco.pipeline.transformer.calm.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.generators.CalmRecordGenerators

class CalmRecordOpsTest extends AnyFunSpec with Matchers with CalmRecordGenerators with CalmRecordOps {
  it("skips values which are just whitespace") {
    val record = createCalmRecordWith(("AccessConditions", "  "))

    record.getList("AccessConditions") shouldBe Nil
  }

  it("skips values which are just a single period") {
    val record = createCalmRecordWith(("AccessStatus", "."))

    record.getList("AccessStatus") shouldBe Nil
  }
}
