package uk.ac.wellcome.models.work.internal

import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.generators.InstantRangeGenerators

class InstantRangeTest
    extends FunSpec
    with Matchers
    with InstantRangeGenerators {

  it("parses valid date labels") {
    val instantRange = createInstantRangeWith(
      "1909",
      "1909-01-01T00:00",
      "1909-12-31T23:59:59.999999999",
      inferred = false)

    val inferredInstantRange = createInstantRangeWith(
      "[2123]",
      "2123-01-01T00:00",
      "2123-12-31T23:59:59.999999999",
      inferred = true)

    forAll(
      Table(
        ("label", "parsedInstantRange"),
        ("1909", instantRange),
        ("[2123]", inferredInstantRange),
      )) { (label, parsedInstantRange) =>
      InstantRange.parse(label) shouldBe Some(parsedInstantRange)
    }
  }

  it("does not parse invalid date labels") {
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
      InstantRange.parse(label) shouldBe None
    }
  }
}
