package weco.catalogue.internal_model.work

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.RandomGenerators

class InstantRangeTest
    extends AnyFunSpec
    with Matchers
    with RandomGenerators {

  describe("unapply") {
    it("extracts an AfterDate") {
      val from = randomInstant
      val to = randomInstant

      val range = InstantRange(from = from, to = to)
      val afterRange = InstantRange.after(range)

      afterRange match {
        case InstantRange.AfterDate(extractedFrom) => extractedFrom shouldBe from
        case _                                     => assert(false)
      }
    }

    it("extracts a BeforeDate") {
      val from = randomInstant
      val to = randomInstant

      val range = InstantRange(from = from, to = to)
      val beforeRange = InstantRange.before(range)

      beforeRange match {
        case InstantRange.BeforeDate(extractedTo) => extractedTo shouldBe to
        case _                                    => assert(false)
      }
    }

    it("extracts a regular InstantDate") {
      val from = randomInstant
      val to = randomInstant

      val range = InstantRange(from = from, to = to)

      range match {
        case InstantRange.AfterDate(from) => assert(false)
        case InstantRange.BeforeDate(to)  => assert(false)
        case InstantRange(extractedFrom, extractedTo, _) =>
          extractedFrom shouldBe from
          extractedTo shouldBe to
      }
    }
  }
}
