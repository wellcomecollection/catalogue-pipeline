package weco.pipeline.transformer.calm.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.generators.CalmRecordGenerators

class CalmRecordOpsTest
    extends AnyFunSpec
    with Matchers
    with CalmRecordGenerators
    with CalmRecordOps {
  it("ignores non-breaking spaces") {
    val record = createCalmRecordWith(
      ("LocationOfDuplicates", "\u00a0")
    )

    record.getList("LocationOfDuplicates") shouldBe empty
  }
}
