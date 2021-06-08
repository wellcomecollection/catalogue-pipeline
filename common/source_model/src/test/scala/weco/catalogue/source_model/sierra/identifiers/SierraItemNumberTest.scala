package weco.catalogue.source_model.sierra.identifiers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SierraItemNumberTest extends AnyFunSpec with Matchers {
  it("create an ItemNumber from a 7-digit ID") {
    SierraItemNumber("1000363")
  }

  it("creates an ItemNumber from an ID with check digit and prefix") {
    SierraItemNumber("i10003630") shouldBe SierraItemNumber("1000363")
  }

  it("rejects an ItemNumber that has the wrong check digit or prefix") {
    intercept[IllegalArgumentException] {
      SierraItemNumber("b10003630")
    }

    intercept[IllegalArgumentException] {
      SierraItemNumber("i1000363x")
    }
  }
}
