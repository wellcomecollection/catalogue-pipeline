package weco.catalogue.internal_model.identifiers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class RelationPathTest extends AnyFunSpec with Matchers {
  it("can be joined") {
    RelationPath("PP/CRI").join(RelationPath("A/1")) shouldBe RelationPath("PP/CRI/A/1")
  }
}
