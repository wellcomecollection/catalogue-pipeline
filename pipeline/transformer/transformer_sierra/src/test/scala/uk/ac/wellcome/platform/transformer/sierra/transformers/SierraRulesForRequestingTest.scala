package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import uk.ac.wellcome.platform.transformer.sierra.generators.SierraDataGenerators
import uk.ac.wellcome.platform.transformer.sierra.source.FixedField

class SierraRulesForRequestingTest extends AnyFunSpec with Matchers with SierraDataGenerators with TableDrivenPropertyChecks {
  it("blocks an item from the strong room") {
    val item = createSierraItemDataWith(
      fixedFields = Map("97" -> FixedField(label = "IMESSAGE", value = "x"))
    )

    SierraRulesForRequesting(item) shouldBe NotRequestable("This item belongs in the Strongroom")
  }

  it("blocks an item based on the status") {
    val testCases = Table(
      ("status", "expectedMessage"),
      ("m", Some("This item is missing.")),
      ("s", Some("This item is on search.")),
      ("x", Some("This item is withdrawn.")),
      ("r", Some("This item is unavailable.")),
      ("z", None),
      ("v", Some("This item is with conservation.")),
      ("h", Some("This item is closed.")),
      ("b", Some("Please request top item.")),
      ("c", Some("Please request top item.")),
      ("d", Some("On new books display.")),
      ("e", Some("On exhibition. Please ask at Enquiry Desk.")),
      ("y", None),

    )

    forAll(testCases) { case (status, expectedMessage) =>
      val item = createSierraItemDataWith(
        fixedFields = Map("88" -> FixedField(label = "STATUS", value = status))
      )

      SierraRulesForRequesting(item) shouldBe NotRequestable(expectedMessage)
    }
  }

  it("blocks an item if fixed field 87 (loan rule) is non-zero") {
    val item = createSierraItemDataWith(
      fixedFields = Map("87" -> FixedField(label = "LOANRULE", value = "1"))
    )

    SierraRulesForRequesting(item) shouldBe NotRequestable("Item is in use by another reader. Please ask at Enquiry Desk.")
  }

  it("blocks an item if fixed field 88 (status) is !") {
    val item = createSierraItemDataWith(
      fixedFields = Map("88" -> FixedField(label = "STATUS", value = "!"))
    )

    SierraRulesForRequesting(item) shouldBe NotRequestable("Item is in use by another reader. Please ask at Enquiry Desk.")
  }

  it("does not block an item if fixed field 87 (loan rule) is zero") {
    val item = createSierraItemDataWith(
      fixedFields = Map("87" -> FixedField(label = "LOANRULE", value = "0"))
    )

    SierraRulesForRequesting(item) shouldBe Requestable
  }

  describe("blocks an item based on fixed field 79 (location)") {
    it("if it's part of the Medical Film & Audio Library") {
      val testCases = Table("mfgmc", "mfinc", "mfwcm", "hmfac", "mfulc")

      forAll(testCases) {
        assertBlockedWith(_, expectedMessage = "Item cannot be requested online. Please contact Medical Film & Audio Library.   Email: mfac@wellcome.ac.uk. Telephone: +44 (0)20 76118596/97.")
      }
    }

    it("if it needs a manual request") {
      val testCases = Table("dbiaa", "dcoaa", "dinad", "dinop", "dinsd", "dints", "dpoaa", "dimgs", "dhuaa", "dimgs", "dingo", "dpleg", "dpuih", "gblip", "ofvds")

      forAll(testCases) {
        assertBlockedWith(_, expectedMessage = "This item cannot be requested online. Please place a manual request.")
      }
    }

    it("if it's with Information Services") {
      val testCases = Table("isvid", "iscdr")

      forAll(testCases) {
        assertBlockedWith(_, expectedMessage = "Item cannot be requested online. Please ask at Information Service desk, email: infoserv@wellcome.ac.uk or telephone +44 (0)20 7611 8722.")
      }
    }

    it("if it's on the open shelves") {
      val testCases = Table("isope", "isref",
        // Note: this is one of the location codes matched by this rule, but it
        // matches another rule higher up, so it's commented out.
        // "gblip",
        "wghib", "wghig", "wghip", "wghir", "wghxb", "wghxg", "wghxp", "wghxr", "wgmem", "wgmxm", "wgpvm", "wgsee", "wgsem", "wgser", "wqrfc", "wqrfd", "wqrfe", "wqrfp", "wqrfr", "wslob", "wslom", "wslor", "wslox", "wsref", "hgslr", "wsrex")

      forAll(testCases) {
        assertBlockedWith(_, expectedMessage = "Item is on open shelves.  Check Location and Shelfmark for location details.")
      }
    }

    def assertBlockedWith(locationCode: String, expectedMessage: String): Assertion = {
      val item = createSierraItemDataWith(
        fixedFields = Map("79" -> FixedField(label = "LOCATION", value = locationCode))
      )

      SierraRulesForRequesting(item) shouldBe NotRequestable(expectedMessage)
    }
  }

  it("allows an item that does not match any rules") {
    val item = createSierraItemData

    SierraRulesForRequesting(item) shouldBe Requestable
  }
}
