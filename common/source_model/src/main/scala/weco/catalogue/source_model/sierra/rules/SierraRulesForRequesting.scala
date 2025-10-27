package weco.catalogue.source_model.sierra.rules

import weco.sierra.models.data.SierraItemData

/** The Rules for Requesting are a set of rules in Sierra that can block an item
  * from being requested, and if so, optionally explain to the user why an item
  * can't be requested.
  *
  * This object translates the rules from the MARC-like syntax into Scala. The
  * original rules are included for reference and to help apply updates, along
  * with explanations of the syntax.
  *
  * If you disagree with one of these rules, you have two options:
  *
  * 1) Discuss the change with Library Systems Support and get it changed in
  * Sierra proper, then update this object to match 2) Work around it elsewhere
  * in our applications
  *
  * But don't modify these rules without updating the canonical set in Sierra --
  * this is meant to be a 1:1 reflection of those rules, not a mix of Sierra
  * rules and platform logic.
  *
  * The type hierarchy for NotRequestable is to allow us to pattern match in our
  * downstream applications without being tied to the exact wording of the
  * messages -- so if they get changed in Sierra, we can change them without
  * breaking our other code.
  *
  * Relevant Sierra docs:
  *
  *   - Rules for Requesting syntax
  *     https://documentation.iii.com/sierrahelp/Content/sgasaa/sgasaa_requestrl.html
  *   - Fixed fields on items
  *     https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html
  *   - Variable length fields on items
  *     https://documentation.iii.com/sierrahelp/Content/sril/sril_records_varfld_types_item.html
  *
  * This was last checked against Sierra based on a set of rules provided by
  * Louise Simon on 20th March 2024
  * https://wellcome.slack.com/archives/C3TQSF63C/p1710937847843969
  */
object SierraRulesForRequesting {
  def apply(itemData: SierraItemData): RulesForRequestingResult =
    itemData match {

      // These cases cover the lines:
      //
      //    q|i||88||=|m||This item is missing.
      //    q|i||88||=|s||This item is on search.
      //    q|i||88||=|x||This item is withdrawn.
      //    q|i||88||=|r||This item is unavailable.
      //    q|i||88||=|z||
      //    q|i||88||=|v||This item is with conservation.
      //    q|i||88||=|h||This item is closed.
      //    q|i||88||=|g||Safeguarded item.
      //
      // These rules mean "if fixed field 88 on the item has a given value,
      // show this message".
      case i if i.fixedField("88").contains("m") =>
        NotRequestable.ItemMissing("This item is missing.")
      case i if i.fixedField("88").contains("s") =>
        NotRequestable.ItemOnSearch("This item is on search.")
      case i if i.fixedField("88").contains("x") =>
        NotRequestable.ItemWithdrawn("This item is withdrawn.")
      case i if i.fixedField("88").contains("r") =>
        NotRequestable.ItemUnavailable()
      case i if i.fixedField("88").contains("z") =>
        NotRequestable.NoPublicMessage("fixed field 88 = z")
      case i if i.fixedField("88").contains("v") =>
        NotRequestable.AtConservation("This item is with conservation.")
      case i if i.fixedField("88").contains("h") =>
        NotRequestable.ItemClosed("This item is closed.")
      case i if i.fixedField("88").contains("g") =>
        NotRequestable.SafeguardedItem("Safeguarded item.")

      // This line covers the case:
      //
      // q|i||88||=|j||Deny item request if catalogue record has data issues.
      //
      // This rule is intended to deny item requests if a catalogue record has data issues.
      case i if i.fixedField("88").contains("j") =>
        NotRequestable.ItemUnavailable()

      // These cases cover the lines:
      //
      //    v|i||88||=|b||
      //    q|i||88||=|c||Please request top item.
      //
      // These are similar to the rules above; the difference is that the 'v' means
      // "if this line or the next line matches".  The 'q' means 'end of rule'.
      case i
          if i
            .fixedField("88")
            .contains("b") || i.fixedField("88").contains("c") =>
        NotRequestable.RequestTopItem("Please request top item.")

      // These cases cover the lines:
      //
      //    q|i||88||=|d||On new books display.
      //    q|i||88||=|e||On exhibition. Please ask at Enquiry Desk.
      //    q|i||88||=|y||
      //
      // These are the same as the checks above.
      case i if i.fixedField("88").contains("d") =>
        NotRequestable.OnNewBooksDisplay("On new books display.")
      case i if i.fixedField("88").contains("e") =>
        NotRequestable.OnExhibition(
          "On exhibition. Please ask at Enquiry Desk."
        )
      case i if i.fixedField("88").contains("y") =>
        NotRequestable.NoPublicMessage("fixed field 88 = y")

      // These cases cover the lines:
      //
      //    v|i||87||~|0||
      //    v|i|8|||e|||
      //    q|i||88||=|!||Item is in use by another reader. Please ask at Enquiry Desk.
      //
      // How they work:
      //
      //    v|i||87||~|0||      # If fixed field 87 (loan rule) is not-equal to zero OR
      //    v|i|8|||e|||        # If variable field with tag 8 exists OR
      //    q|i||88||=|!||      # If fixed field 88 (status) equals '!'
      //
      // Notes:
      //    - Some items are missing fixed field 87 but are requestable using Encore.
      //      The Sierra API docs suggest the default loan rule is '0', so I'm assuming
      //      a missing FF87 doesn't block requesting.
      //    - I haven't found an example of an item with tag 8, so I'm skipping that rule
      //      for now.  TODO: Find an example of this.
      //
      case i
          if i.fixedField("87").getOrElse("0") != "0" || i
            .fixedField("88")
            .contains("!") =>
        NotRequestable.InUseByAnotherReader(
          "Item is in use by another reader. Please ask at Enquiry Desk."
        )

      // These cases cover the lines:
      //
      //    v|i||108||=|n||
      //    #ls Line above opacmsg = manual request for vs 27/08/24
      //    v|i||108||=|a||
      //    #ls Line above opacmsg = By appointment for vs 27/08/24
      //    v|i||108||=|p||
      //    #ls Line above opacmsg = By approval for vs 27/08/24
      case i
          if i
            .fixedField("108")
            .containsAnyOf("n", "a", "p") =>
        NotRequestable.NeedsManualRequest()

      // These cases cover the lines:
      //
      //    q|i||108||=|u||
      //    #ls Line above opacmsg = Unavailable for vs 27/08/24
      case i
          if i
            .fixedField("108")
            .contains("u") =>
        NotRequestable.ItemUnavailable()

      // These cases cover the lines:
      //
      //    v|i||79||=|mfgmc||
      //    v|i||79||=|mfinc||
      //    v|i||79||=|mfwcm||
      //    v|i||79||=|hmfac||
      //    q|i||79||=|mfulc||Item cannot be requested online. Please contact Medical Film & Audio Library.   Email: mfac@wellcome.ac.uk. Telephone: +44 (0)20 76118596/97.
      //
      case i
          if i
            .fixedField("79")
            .containsAnyOf("mfgmc", "mfinc", "mfwcm", "hmfac", "mfulc") =>
        NotRequestable.ContactUs(
          "Item cannot be requested online. Please contact Medical Film & Audio Library.   Email: mfac@wellcome.ac.uk. Telephone: +44 (0)20 76118596/97."
        )

      // These cases cover the lines:
      //
      //    v|i||79||=|dbiaa||
      //    v|i||79||=|dcoaa||
      //    v|i||79||=|dinad||
      //    v|i||79||=|dinop||
      //    v|i||79||=|dinsd||
      //    v|i||79||=|dints||
      //    v|i||79||=|dpoaa||
      //    v|i||79||=|dimgs||
      //    v|i||79||=|dhuaa||
      //    v|i||79||=|dimgs||
      //    v|i||79||=|dingo||
      //    v|i||79||=|dpleg||
      //    v|i||79||=|dpuih||
      //    v|i||79||=|enhal||
      //    #line above added so that laptops (enhal) cannot be requested online  ls 30/05/24
      //    v|i||79||=|gblip||
      //    v|i||79||=|harcl||
      //    # line above added so that Offsite deepstore material is not requestable.  LS 13/06/24
      //    q|i||79||=|ofvds||This item cannot be requested online. Please place a manual request.
      //
      case i
          if i
            .fixedField("79")
            .containsAnyOf(
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
            ) =>
        NotRequestable.NeedsManualRequest()

      // These cases cover the lines:
      //
      //    v|i||79||=|harcl||
      //    # line above added so that Offsite deepstore material is not requestable.  LS 13/06/24
      //
      case i
          if i
            .fixedField("79")
            .containsAnyOf(
              "harcl"
            ) =>
        NotRequestable.ItemUnavailable()

      // These cases cover the lines:
      //
      //    v|i||79||=|isvid||
      //    q|i||79||=|iscdr||Item cannot be requested online. Please ask at Information Service desk, email: infoserv@wellcome.ac.uk or telephone +44 (0)20 7611 8722.
      //
      case i if i.fixedField("79").containsAnyOf("isvid", "iscdr") =>
        NotRequestable.ContactUs(
          "Item cannot be requested online. Please ask at Information Service desk, email: infoserv@wellcome.ac.uk or telephone +44 (0)20 7611 8722."
        )

      // These cases cover the lines:
      //
      //    v|i||79||=|isope||
      //    v|i||79||=|isref||
      //    v|i||79||=|gblip||
      //    v|i||79||=|wghib||
      //    v|i||79||=|wghig||
      //    v|i||79||=|wghip||
      //    v|i||79||=|wghir||
      //    v|i||79||=|wghxb||
      //    v|i||79||=|wghxg||
      //    v|i||79||=|wghxp||
      //    v|i||79||=|wghxr||
      //    v|i||79||=|wgmem||
      //    v|i||79||=|wgmxm||
      //    v|i||79||=|wgpvm||
      //    v|i||79||=|wgsee||
      //    v|i||79||=|wgsem||
      //    v|i||79||=|wgser||
      //    v|i||79||=|wqrfc||
      //    v|i||79||=|wqrfd||
      //    v|i||79||=|wqrfe||
      //    v|i||79||=|wqrfp||
      //    v|i||79||=|wqrfr||
      //    v|i||79||=|wslob||
      //    v|i||79||=|wslom||
      //    v|i||79||=|wslor||
      //    v|i||79||=|wslox||
      //    v|i||79||=|wsref||
      //    v|i||79||=|hgslr||
      //    q|i||79||=|wsrex||Item is on open shelves.  Check Location and Shelfmark for location details.
      //
      case i
          if i
            .fixedField("79")
            .containsAnyOf(
              "isope",
              "isref",
              "gblip",
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
            ) =>
        NotRequestable.OnOpenShelves(
          "Item is on open shelves.  Check Location and Shelfmark for location details."
        )

      // These cases cover the lines:
      //
      //    q|i||61||=|22||Item is on Exhibition Reserve. Please ask at the Enquiry Desk
      //    q|i||61||=|17||
      //    q|i||61||=|18||
      //    q|i||61||=|15||
      //
      //    #v|i||61||=|4||
      //    v|i||61||=|14||
      //    v|i||79||=|ofvn1||
      //    v|i||79||=|scmwc||
      //    v|i||79||=|sgmoh||
      //    v|i||79||=|somet||
      //    v|i||79||=|somge||
      //    v|i||79||=|sompr||
      //    q|i||79||=|somsy||Please complete a manual request slip.  This item cannot be requested online.
      //
      case i if i.fixedField("61").contains("22") =>
        NotRequestable.OnExhibition(
          "Item is on Exhibition Reserve. Please ask at the Enquiry Desk"
        )

      case i if i.fixedField("61").containsAnyOf("17", "18", "15") =>
        NotRequestable.NoPublicMessage(
          s"fixed field 61 = ${i.fixedField("61").get} (${i.fixedFields("61").display.getOrElse("<none>")})"
        )

      case i
          if i.fixedField("61").containsAnyOf("14") || i
            .fixedField("79")
            .containsAnyOf(
              "ofvn1",
              "scmwc",
              "sgmoh",
              "somet",
              "somge",
              "sompr",
              "somsy"
            ) =>
        NotRequestable.NeedsManualRequest()

      // This case covers the line:
      //
      //    q|i||79||=|sepep||
      //
      case i if i.fixedField("79").contains("sepep") =>
        NotRequestable.NoPublicMessage("fixed field 79 = sepep")

      // This case covers the lines:
      //
      //    v|i||79||=|sc#ac||
      //    v|i||79||=|sc#ra||
      //    v|i||79||=|sc#wa||
      //    v|i||79||=|sc#wf||
      //    v|i||79||=|swm#m||
      //    v|i||79||=|swm#o||
      //    v|i||79||=|swm#1||
      //    v|i||79||=|swm#2||
      //    v|i||79||=|swm#3||
      //    v|i||79||=|swm#4||
      //    v|i||79||=|swm#5||
      //    v|i||79||=|swm#6||
      //    q|i||79||=|swm#7||Item not available due to provisions of Data Protection Act. Return to Archives catalogue to see when this file will be opened.
      //
      case i
          if i
            .fixedField("79")
            .containsAnyOf(
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
            ) =>
        NotRequestable.ItemUnavailable(
          "Item not available due to provisions of Data Protection Act. Return to Archives catalogue to see when this file will be opened."
        )

      // There's a rule in the Rules for Requesting that goes:
      //
      //    q|b|^||i|=|0|This item is on order and cannot be requested yet.  Please ask at an Enquiry Desk.
      //
      // This rule means "if this bib has no items linked, it cannot be requested".
      // We don't allow requesting on bibs, so I haven't implemented this rule.

      // This case covers the lines:
      //
      //    v|i||79||=|temp1||
      //    v|i||79||=|temp2||
      //    v|i||79||=|temp3||
      //    v|i||79||=|temp4||
      //    v|i||79||=|temp5||
      //    q|i||79||=|temp6||At digitisation and temporarily unavailable.
      //
      case i
          if i
            .fixedField("79")
            .containsAnyOf(
              "temp1",
              "temp2",
              "temp3",
              "temp4",
              "temp5",
              "temp6"
            ) =>
        NotRequestable.AtDigitisation(
          "At digitisation and temporarily unavailable."
        )

      // This case covers the lines:
      //
      //    v|i||79||=|rm001||
      //    q|i||79||=|rmdda||
      //
      case i if i.fixedField("79").containsAnyOf("rm001", "rmdda") =>
        NotRequestable.NoPublicMessage(
          s"fixed field 79 = ${i.fixedField("79").get}"
        )

      case _ => Requestable
    }

  private implicit class ItemDataOps(itemData: SierraItemData) {
    def fixedField(code: String): Option[String] =
      itemData.fixedFields.get(code).map { _.value.trim }
  }

  private implicit class OptionalStringOps(s: Option[String]) {
    def containsAnyOf(substrings: String*): Boolean =
      substrings.exists(s.contains(_))
  }
}
