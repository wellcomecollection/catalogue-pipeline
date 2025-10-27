package weco.catalogue.source_model.sierra.rules

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.FixedField

class SierraRulesForRequestingTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators
    with TableDrivenPropertyChecks {
  it("blocks an item based on the status") {
    val testCases = Table(
      ("status", "expectedMessage"),
      ("m", NotRequestable.ItemMissing("This item is missing.")),
      ("s", NotRequestable.ItemOnSearch("This item is on search.")),
      ("x", NotRequestable.ItemWithdrawn("This item is withdrawn.")),
      ("r", NotRequestable.ItemUnavailable("This item is unavailable.")),
      ("j", NotRequestable.ItemUnavailable("This item is unavailable.")),
      ("z", NotRequestable.NoPublicMessage("fixed field 88 = z")),
      ("v", NotRequestable.AtConservation("This item is with conservation.")),
      ("h", NotRequestable.ItemClosed("This item is closed.")),
      ("b", NotRequestable.RequestTopItem("Please request top item.")),
      ("c", NotRequestable.RequestTopItem("Please request top item.")),
      ("d", NotRequestable.OnNewBooksDisplay("On new books display.")),
      (
        "e",
        NotRequestable.OnExhibition(
          "On exhibition. Please ask at Enquiry Desk."
        )
      ),
      ("y", NotRequestable.NoPublicMessage("fixed field 88 = y")),
      ("g", NotRequestable.SafeguardedItem("Safeguarded item."))
    )

    forAll(testCases) {
      case (status, expectedResult) =>
        val item = createSierraItemDataWith(
          fixedFields =
            Map("88" -> FixedField(label = "STATUS", value = status))
        )

        SierraRulesForRequesting(item) shouldBe expectedResult
    }
  }

  it("blocks an item if fixed field 87 (loan rule) is non-zero") {
    val item = createSierraItemDataWith(
      fixedFields = Map("87" -> FixedField(label = "LOANRULE", value = "1"))
    )

    SierraRulesForRequesting(item) shouldBe NotRequestable.InUseByAnotherReader(
      "Item is in use by another reader. Please ask at Enquiry Desk."
    )
  }

  it("blocks an item if fixed field 88 (status) is !") {
    val item = createSierraItemDataWith(
      fixedFields = Map("88" -> FixedField(label = "STATUS", value = "!"))
    )

    SierraRulesForRequesting(item) shouldBe NotRequestable.InUseByAnotherReader(
      "Item is in use by another reader. Please ask at Enquiry Desk."
    )
  }

  it("blocks an item if fixed field 108 (status) is n,a,p or u") {
    List("n", "a", "p").map {
      status =>
        val item = createSierraItemDataWith(
          fixedFields =
            Map("108" -> FixedField(label = "STATUS", value = status))
        )

        SierraRulesForRequesting(item) shouldBe a[
          NotRequestable.NeedsManualRequest
        ]
    }

    val item = createSierraItemDataWith(
      fixedFields = Map("108" -> FixedField(label = "STATUS", value = "u"))
    )

    SierraRulesForRequesting(item) shouldBe a[NotRequestable.ItemUnavailable]
  }

  it("does not block an item if fixed field 87 (loan rule) is zero") {
    val item = createSierraItemDataWith(
      fixedFields = Map("87" -> FixedField(label = "LOANRULE", value = "0"))
    )

    SierraRulesForRequesting(item) shouldBe Requestable
  }

  describe("blocks an item based on fixed field 79 (location)") {
    it("if it's part of the Medical Film & Audio Library") {
      val testCases =
        Table("locationCode", "mfgmc", "mfinc", "mfwcm", "hmfac", "mfulc")

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedResult = NotRequestable.ContactUs(
            "Item cannot be requested online. Please contact Medical Film & Audio Library.   Email: mfac@wellcome.ac.uk. Telephone: +44 (0)20 76118596/97."
          )
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
        "enhal",
        "gblip",
        "ofvds"
      )

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedResult = NotRequestable.NeedsManualRequest(
            "This item cannot be requested online. Please place a manual request."
          )
        )
      }
    }

    it("if it has a generic unavailable message") {
      val testCases = Table(
        "locationCode",
        "harcl"
      )

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedResult = NotRequestable.ItemUnavailable(
            "This item is unavailable."
          )
        )
      }
    }

    it("if it's with Information Services") {
      val testCases = Table("locationCode", "isvid", "iscdr")

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedResult = NotRequestable.ContactUs(
            "Item cannot be requested online. Please ask at Information Service desk, email: infoserv@wellcome.ac.uk or telephone +44 (0)20 7611 8722."
          )
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
          expectedResult = NotRequestable.OnOpenShelves(
            "Item is on open shelves.  Check Location and Shelfmark for location details."
          )
        )
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
        "sompr",
        "somsy"
      )

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedResult = NotRequestable.NeedsManualRequest(
            "This item cannot be requested online. Please place a manual request."
          )
        )
      }
    }

    it(
      "if it's one of the location codes blocked with no user-facing message"
    ) {
      val testCases = Table("locationCode", "sepep", "rm001", "rmdda")

      forAll(testCases) {
        locationCode =>
          assertBlockedWith(
            _,
            expectedResult =
              NotRequestable.NoPublicMessage(s"fixed field 79 = $locationCode")
          )
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
        "swm#7"
      )

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedResult = NotRequestable.ItemUnavailable(
            "Item not available due to provisions of Data Protection Act. Return to Archives catalogue to see when this file will be opened."
          )
        )
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
        "temp6"
      )

      forAll(testCases) {
        assertBlockedWith(
          _,
          expectedResult = NotRequestable.AtDigitisation(
            "At digitisation and temporarily unavailable."
          )
        )
      }
    }

    def assertBlockedWith(
      locationCode: String,
      expectedResult: RulesForRequestingResult
    ): Assertion = {
      val item = createSierraItemDataWith(
        fixedFields =
          Map("79" -> FixedField(label = "LOCATION", value = locationCode))
      )

      SierraRulesForRequesting(item) shouldBe expectedResult
    }
  }

  it("blocks an item based on fixed field 61 (item type)") {
    val testCases = Table(
      ("itemType", "expectedMessage"),
      (
        "22",
        NotRequestable.OnExhibition(
          "Item is on Exhibition Reserve. Please ask at the Enquiry Desk"
        )
      ),
      ("17", NotRequestable.NoPublicMessage("fixed field 61 = 17 (<none>)")),
      ("18", NotRequestable.NoPublicMessage("fixed field 61 = 18 (<none>)")),
      ("15", NotRequestable.NoPublicMessage("fixed field 61 = 15 (<none>)")),
      (
        "14",
        NotRequestable.NeedsManualRequest(
          "This item cannot be requested online. Please place a manual request."
        )
      ),
      // Item type 4 is requestable, included here to verify that other
      // item types are not inadvertently blocked.
      (
        "4",
        Requestable
      ),
    )

    forAll(testCases) {
      case (itemType, expectedResult) =>
        val item = createSierraItemDataWith(
          fixedFields =
            Map("61" -> FixedField(label = "I TYPE", value = itemType))
        )

        SierraRulesForRequesting(item) shouldBe expectedResult
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
      )
    )

    forAll(testCases) {
      SierraRulesForRequesting(_) shouldBe Requestable
    }
  }
}
