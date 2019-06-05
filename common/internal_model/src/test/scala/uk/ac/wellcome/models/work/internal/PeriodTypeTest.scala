package uk.ac.wellcome.models.work.internal

import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSpec, Matchers}

class PeriodTest extends FunSpec with Matchers {
  it("should add a range to a when there id a parsable label") {
    forAll(
      Table(
        "label",
        "1909",
        "[2123]",
      )) { label =>
      Period(label).range shouldNot be(None)
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
      Period(label).range shouldBe None
    }
  }
}
