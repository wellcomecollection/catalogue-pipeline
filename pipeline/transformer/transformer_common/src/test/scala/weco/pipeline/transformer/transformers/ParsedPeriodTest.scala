package weco.pipeline.transformer.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.matchers.should.Matchers

class ParsedPeriodTest extends AnyFunSpec with Matchers {
  it("should add a range to a when there id a parsable label") {
    forAll(
      Table(
        "label",
        "1909",
        "[2123]",
      )) { label =>
      ParsedPeriod(label).range shouldNot be(None)
    }
  }

  it("should not add a range when the dates aren't parsable") {
    forAll(
      Table(
        "label",
        "nineteen sixty what, nineteen sixty who",
        "123",
        "23Y5",
        "[21233]",
        "[21u6]",
        "[^216]",
        "[216]",
      )) { label =>
      ParsedPeriod(label).range shouldBe None
    }
  }
}
