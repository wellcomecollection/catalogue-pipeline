package weco.catalogue.source_model.sierra

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.source_model.generators.SierraDataGenerators
import weco.catalogue.source_model.sierra
import weco.catalogue.source_model.sierra.marc.FixedField

class SierraRulesForRequestingTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators
    with TableDrivenPropertyChecks {
  it("blocks an item from the strong room") {
    val item = createSierraItemDataWith(
      fixedFields = Map("97" -> FixedField(label = "IMESSAGE", value = "x"))
    )

    SierraRulesForRequesting(item) shouldBe NotRequestable(
      "This item belongs in the Strongroom")
  }

  it("blocks an item with fixed field 97 (imessage) = j") {
    val item = createSierraItemDataWith(
      fixedFields = Map("97" -> FixedField(label = "IMESSAGE", value = "j"))
    )

    sierra.SierraRulesForRequesting(item) shouldBe NotRequestable()
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

    forAll(testCases) {
      case (status, expectedMessage) =>
        val item = createSierraItemDataWith(
          fixedFields =
            Map("88" -> FixedField(label = "STATUS", value = status))
        )

        sierra.SierraRulesForRequesting(item) shouldBe NotRequestable(
          expectedMessage)
    }
  }

  it("blocks an item if fixed field 87 (loan rule) is non-zero") {
    val item = createSierraItemDataWith(
      fixedFields = Map("87" -> FixedField(label = "LOANRULE", value = "1"))
    )

    sierra.SierraRulesForRequesting(item) shouldBe NotRequestable(
      "Item is in use by another reader. Please ask at Enquiry Desk.")
  }

  it("blocks an item if fixed field 88 (status) is !") {
    val item = createSierraItemDataWith(
      fixedFields = Map("88" -> FixedField(label = "STATUS", value = "!"))
    )

    sierra.SierraRulesForRequesting(item) shouldBe NotRequestable(
      "Item is in use by another reader. Please ask at Enquiry Desk.")
  }

  it("does not block an item if fixed field 87 (loan rule) is zero") {
    val item = createSierraItemDataWith(
      fixedFields = Map("87" -> FixedField(label = "LOANRULE", value = "0"))
    )

    sierra.SierraRulesForRequesting(item) shouldBe Requestable
  }

  describe("blocks an item based on fixed field 79 (location)") {
    it("if it's part of the Medical Film & Audio Library") {
      val testCases =
        Table("locationCode", "mfgmc", "mfinc", "mfwcm", "hmfac", "mfulc")

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedMessage =
            "Item cannot be requested online. Please contact Medical Film & Audio Library.   Email: mfac@wellcome.ac.uk. Telephone: +44 (0)20 76118596/97."
        )
      }
    }

    it("if it needs a manual request") {
      val testCases = Table(
        "locationCode",
        "dbiaa",
        "dcoaa",
        "dinad",
        "dinop",
        "dinsd",
        "dints",
        "dpoaa",
        "dimgs",
        "dhuaa",
        "dimgs",
        "dingo",
        "dpleg",
        "dpuih",
        "gblip",
        "ofvds")

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedMessage =
            "This item cannot be requested online. Please place a manual request.")
      }
    }

    it("if it's with Information Services") {
      val testCases = Table("locationCode", "isvid", "iscdr")

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedMessage =
            "Item cannot be requested online. Please ask at Information Service desk, email: infoserv@wellcome.ac.uk or telephone +44 (0)20 7611 8722."
        )
      }
    }

    it("if it's on the open shelves") {
      val testCases = Table(
        "locationCode",
        "isope",
        "isref",
        // Note: this is one of the location codes matched by this rule, but it
        // matches another rule higher up, so it's commented out.
        // "gblip",
        "wghib",
        "wghig",
        "wghip",
        "wghir",
        "wghxb",
        "wghxg",
        "wghxp",
        "wghxr",
        "wgmem",
        "wgmxm",
        "wgpvm",
        "wgsee",
        "wgsem",
        "wgser",
        "wqrfc",
        "wqrfd",
        "wqrfe",
        "wqrfp",
        "wqrfr",
        "wslob",
        "wslom",
        "wslor",
        "wslox",
        "wsref",
        "hgslr",
        "wsrex"
      )

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedMessage =
            "Item is on open shelves.  Check Location and Shelfmark for location details.")
      }
    }

    it("if it needs a manual request slip") {
      val testCases = Table(
        "locationCode",
        "ofvn1",
        "scmwc",
        "sgmoh",
        "somet",
        "somge",
        "somhe",
        "somhi",
        "somja",
        "sompa",
        "sompr",
        "somsy")

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedMessage =
            "Please complete a manual request slip.  This item cannot be requested online.")
      }
    }

    it("if it's one of the location codes blocked with no user-facing message") {
      val testCases = Table("locationCode", "sepep", "rm001", "rmdda")

      forAll(testCases) { locationCode =>
        val item = createSierraItemDataWith(
          fixedFields =
            Map("79" -> FixedField(label = "LOCATION", value = locationCode))
        )

        sierra.SierraRulesForRequesting(item) shouldBe NotRequestable()
      }
    }

    it("if it's closed for Data Protection") {
      val testCases = Table(
        "locationCode",
        "sc#ac",
        "sc#ra",
        "sc#wa",
        "sc#wf",
        "swm#m",
        "swm#o",
        "swm#1",
        "swm#2",
        "swm#3",
        "swm#4",
        "swm#5",
        "swm#6",
        "swm#7")

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedMessage =
            "Item not available due to provisions of Data Protection Act. Return to Archives catalogue to see when this file will be opened.")
      }
    }

    it("if it's at digitisation") {
      val testCases = Table(
        "locationCode",
        "temp1",
        "temp2",
        "temp3",
        "temp4",
        "temp5",
        "temp6")

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedMessage = "At digitisation and temporarily unavailable.")
      }
    }

    def assertBlockedWith(locationCode: String,
                          expectedMessage: String): Assertion = {
      val item = createSierraItemDataWith(
        fixedFields =
          Map("79" -> FixedField(label = "LOCATION", value = locationCode))
      )

      sierra.SierraRulesForRequesting(item) shouldBe NotRequestable(
        expectedMessage)
    }
  }

  it("blocks an item based on fixed field 61 (item type)") {
    val testCases = Table(
      ("itemType", "expectedMessage"),
      (
        "22",
        Some("Item is on Exhibition Reserve. Please ask at the Enquiry Desk")),
      ("17", None),
      ("18", None),
      ("15", None),
      (
        "4",
        Some(
          "Please complete a manual request slip.  This item cannot be requested online.")),
      (
        "14",
        Some(
          "Please complete a manual request slip.  This item cannot be requested online.")),
    )

    forAll(testCases) {
      case (itemType, expectedMessage) =>
        val item = createSierraItemDataWith(
          fixedFields =
            Map("61" -> FixedField(label = "I TYPE", value = itemType))
        )

        sierra.SierraRulesForRequesting(item) shouldBe NotRequestable(
          expectedMessage)
    }
  }

  it("allows an item that does not match any rules") {
    val testCases = Table(
      "item",
      createSierraItemData,
      createSierraItemDataWith(
        fixedFields = Map(
          "79" -> FixedField(label = "LOCATION", value = "sicon")
        )
      ),
      createSierraItemDataWith(
        fixedFields = Map(
          "87" -> FixedField(label = "LOANRULE", value = "0")
        )
      ),
      createSierraItemDataWith(
        fixedFields = Map(
          "61" -> FixedField(label = "I TYPE", value = "5")
        )
      ),
    )

    forAll(testCases) {
      sierra.SierraRulesForRequesting(_) shouldBe Requestable
    }
  }
}
