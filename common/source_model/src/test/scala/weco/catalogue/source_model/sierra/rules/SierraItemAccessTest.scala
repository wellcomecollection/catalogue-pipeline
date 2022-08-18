package weco.catalogue.source_model.sierra.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  LocationType
}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{FixedField, VarField}

class SierraItemAccessTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {

  def createFixedFieldWith(label: String)(value: String, display: String = ""): FixedField =
    FixedField(
      label = label,
      value = value, if (display.isEmpty) { Some(display) } else { None }
    )

  def createLocationWith: (String, String) => FixedField = createFixedFieldWith(label = "LOCATION")
  def createStatusWith: (String, String) => FixedField = createFixedFieldWith(label = "STATUS")
  def createOpacMsgWith: (String, String) => FixedField = createFixedFieldWith(label = "OPACMSG")
  def createItypeWith: (String, String) => FixedField = createFixedFieldWith(label = "ITYPE")

  describe("an item in the closed stores") {
    describe("with no holds") {
      describe("can be requested online") {
        it("if it has no restrictions") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> createLocationWith("scmac", "Closed stores Arch. & MSS"),
              "88" -> createStatusWith("-", "Available"),
              "108" -> createOpacMsgWith("f", "Online request"),
            )
          )

          val (ac, _) = SierraItemAccess(
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe AccessCondition(
            method = AccessMethod.OnlineRequest,
            status = AccessStatus.Open)
        }

        it("if it's restricted") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> createLocationWith("scmac", "Closed stores Arch. & MSS"),
              "88" -> createStatusWith("-", "Available"),
              "108" -> createOpacMsgWith("c", "Restricted"),
            )
          )

          val (ac, _) = SierraItemAccess(
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.OnlineRequest,
              status = AccessStatus.Restricted)
        }
      }

      describe("cannot be requested") {
        it("if it needs a manual request") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "61" -> createItypeWith("4", "serial"),
              "79" -> createLocationWith("sgser", "Closed stores journals"),
              "88" -> createStatusWith("-", "Available"),
              "108" -> createOpacMsgWith("n", "Manual request"),
            )
          )

          val (ac, _) = SierraItemAccess(
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe AccessCondition(method = AccessMethod.ManualRequest)
        }

        it("if it's bound in the top item") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> createLocationWith("bwith", "bound in above"),
              "88" -> createStatusWith("b", "As above"),
              "108" -> createOpacMsgWith("-", "-"),
            )
          )

          val (ac, _) = SierraItemAccess(
            location = None,
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.NotRequestable,
              note = Some("Please request top item."))
        }

        it("if it's contained the top item") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> createLocationWith("cwith", "contained in above"),
              "88" -> createStatusWith("c", "As above"),
              "108" -> createOpacMsgWith("-", "-"),
            )
          )

          val (ac, _) = SierraItemAccess(
            location = None,
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.NotRequestable,
              note = Some("Please request top item."))
        }

        it("if the bib and the item are closed") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> createLocationWith("sc#ac", "Unrequestable Arch. & MSS"),
              "88" -> createStatusWith("h", "Closed"),
              "108" -> createOpacMsgWith("u", "Unavailable"),
            )
          )

          val (ac, _) = SierraItemAccess(
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = AccessStatus.Closed
            )
        }

        it("if the item is unavailable") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> createLocationWith("sgser", "Closed stores journals"),
              "88" -> createStatusWith("r", "Unavailable"),
              "108" -> createOpacMsgWith("u", "Unavailable"),
            )
          )

          val (ac, _) = SierraItemAccess(
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = Some(AccessStatus.TemporarilyUnavailable),
              note = Some(
                "This item is undergoing internal assessment or conservation work.")
            )
        }

        it("if the item is at digitisation") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> createLocationWith("sgser", "Closed stores journals"),
              "88" -> createStatusWith("r", "Unavailable"),
              "108" -> createOpacMsgWith("b", "@ digitisation"),
            )
          )

          val (ac, _) = SierraItemAccess(
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = Some(AccessStatus.TemporarilyUnavailable),
              note = Some(
                "This item is being digitised and is currently unavailable.")
            )
        }

        it("if doesn't double up the note about digitisation") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> createLocationWith("sgser", "Closed stores journals"),
              "88" -> createStatusWith("r", "Unavailable"),
              "108" -> createOpacMsgWith("b", "@ digitisation"),
            ),
            varFields = List(
              VarField(
                fieldTag = "n",
                content =
                  "<p>This item is being digitised and is currently unavailable."
              )
            )
          )

          val (ac, _) = SierraItemAccess(
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = Some(AccessStatus.TemporarilyUnavailable),
              note = Some(
                "This item is being digitised and is currently unavailable.")
            )
        }

        it("if the item is by appointment") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> createLocationWith("scmac", "Closed stores Arch. & MSS"),
              "88" -> createStatusWith("y", "Permission required"),
              "108" -> createOpacMsgWith("a", "By appointment"),
            )
          )

          val (ac, _) = SierraItemAccess(
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.ManualRequest,
              status = AccessStatus.ByAppointment)
        }

        it("if the item needs donor permission") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> createLocationWith("sc#ac", "Unrequestable Arch. & MSS"),
              "88" -> createStatusWith("y", "Permission required"),
              "108" -> createOpacMsgWith("q", "Donor permission"),
            )
          )

          val (ac, _) = SierraItemAccess(
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.ManualRequest,
              status = AccessStatus.PermissionRequired)
        }

        it("if the item is missing") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> createLocationWith("sghi2", "Closed stores Hist. 2"),
              "88" -> createStatusWith("m", "Missing"),
              "108" -> createOpacMsgWith("f", "Online request"),
            )
          )

          val (ac, _) = SierraItemAccess(
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = Some(AccessStatus.Unavailable),
              note = Some("This item is missing.")
            )
        }

        it("if the item is withdrawn") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> createLocationWith("sghx2", "Closed stores Hist. O/S 2"),
              "88" -> createStatusWith("x", "Withdrawn"),
              "108" -> createOpacMsgWith("u", "Unavailable"),
            )
          )

          val (ac, _) = SierraItemAccess(
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = Some(AccessStatus.Unavailable),
              note = Some("This item is withdrawn.")
            )
        }
      }
    }

    describe("that's on hold can't be requested") {
      it("when another reader has placed a hold") {
        val itemData = createSierraItemDataWith(
          holdCount = Some(1),
          fixedFields = Map(
            "79" -> createLocationWith("sgeph",  "Closed stores ephemera"),
            "88" -> createStatusWith("-", "Available"),
            "108" -> createOpacMsgWith("f", "Online request"),
          )
        )

        val (ac, _) = SierraItemAccess(
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe
          AccessCondition(
            method = AccessMethod.NotRequestable,
            status = Some(AccessStatus.TemporarilyUnavailable),
            note = Some(
              "Item is in use by another reader. Please ask at Library Enquiry Desk.")
          )
      }

      it("when an item is on hold for a loan rule") {
        val itemData = createSierraItemDataWith(
          holdCount = Some(1),
          fixedFields = Map(
            "79" -> createLocationWith("sgeph", "Closed stores ephemera"),
            "87" -> createFixedFieldWith("LOANRULE")("5"),
            "88" -> createStatusWith("-", "Available"),
            "108" -> createOpacMsgWith("f", "Online request"),
          )
        )

        val (ac, _) = SierraItemAccess(
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe
          AccessCondition(
            method = AccessMethod.NotRequestable,
            status = Some(AccessStatus.TemporarilyUnavailable),
            note = Some(
              "Item is in use by another reader. Please ask at Library Enquiry Desk.")
          )
      }

      it("when a manual request item is on hold for somebody else") {
        val itemData = createSierraItemDataWith(
          holdCount = Some(1),
          fixedFields = Map(
            "61" -> createItypeWith("4", "serial"),
            "79" -> createLocationWith("sgser", "Closed stores journals"),
            "88" -> createStatusWith("-", "Available"),
            "108" -> createOpacMsgWith("n", "Manual request"),
          )
        )

        val (ac, _) = SierraItemAccess(
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe
          AccessCondition(
            method = AccessMethod.NotRequestable,
            status = Some(AccessStatus.TemporarilyUnavailable),
            note = Some(
              "Item is in use by another reader. Please ask at Library Enquiry Desk.")
          )
      }

      it("when it's on the hold shelf for another reader") {
        val itemData = createSierraItemDataWith(
          holdCount = Some(1),
          fixedFields = Map(
            "79" -> createLocationWith("swms4", "Closed stores WMS 4"),
            "88" -> createStatusWith("!", "On holdshelf"),
            "108" -> createOpacMsgWith("f", "Online request"),
          )
        )

        val (ac, _) = SierraItemAccess(
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe
          AccessCondition(
            method = AccessMethod.NotRequestable,
            status = Some(AccessStatus.TemporarilyUnavailable),
            note = Some(
              "Item is in use by another reader. Please ask at Library Enquiry Desk.")
          )
      }
    }

    describe("gets the right note") {
      it("if there's a display note about manual requesting") {
        val itemData = createSierraItemDataWith(
          fixedFields = Map(
            "61" -> createItypeWith("4", "serial"),
            "79" -> createLocationWith("sgser", "Closed stores journals"),
            "88" -> createStatusWith("-", "Available"),
            "108" -> createOpacMsgWith("n", "Manual request"),
          ),
          varFields = List(
            VarField(
              fieldTag = "n",
              content =
                "Email library@wellcomecollection.org to tell us why you need access. We’ll reply within a week."
            )
          )
        )

        val (ac, note) = SierraItemAccess(
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe AccessCondition(
          method = AccessMethod.ManualRequest,
          note = Some(
            "Email library@wellcomecollection.org to tell us why you need access. We’ll reply within a week.")
        )

        note shouldBe None
      }

      it("doesn't overwrite the note if there's a hold on the item") {
        val itemData = createSierraItemDataWith(
          fixedFields = Map(
            "79" -> createLocationWith("sgser", "Closed stores journals"),
            "88" -> createStatusWith("-", "Available"),
            "108" -> createOpacMsgWith("f", "Online request"),
          ),
          holdCount = Some(1),
          varFields = List(
            VarField(
              fieldTag = "n",
              content =
                "Email library@wellcomecollection.org to tell us why you need access. We’ll reply within a week."
            )
          )
        )

        val (ac, note) = SierraItemAccess(
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac.note shouldBe Some(
          "Item is in use by another reader. Please ask at Library Enquiry Desk.")
        note shouldBe None
      }

      it("if there's a display note with access information") {
        val itemData = createSierraItemDataWith(
          fixedFields = Map(
            "61" -> createItypeWith("4", "serial"),
            "79" -> createLocationWith("hgser", "Offsite"),
            "88" -> createStatusWith("y", "Permission required"),
            "108" -> createOpacMsgWith("a", "By appointment"),
          ),
          varFields = List(
            VarField(
              fieldTag = "n",
              content =
                "Email library@wellcomecollection.org to tell us why you need the physical copy. We'll reply within a week."
            )
          )
        )

        val (ac, _) = SierraItemAccess(
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac.note shouldBe Some(
          "Email library@wellcomecollection.org to tell us why you need the physical copy. We'll reply within a week.")
      }

      it("returns the note if it's unrelated to access data") {
        val itemData = createSierraItemDataWith(
          fixedFields = Map(
            "79" -> createLocationWith("scmac", "Closed stores Arch. & MSS"),
            "88" -> createStatusWith("-", "Available"),
            "108" -> createOpacMsgWith("f", "Online request"),
          ),
          varFields = List(
            VarField(
              fieldTag = "n",
              content = "uncoloured impression on paper mount"
            )
          )
        )

        val (_, Some(note)) = SierraItemAccess(
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        note shouldBe "uncoloured impression on paper mount"
      }
    }
  }

  describe("an item on the open shelves") {
    describe("with no holds or other restrictions") {
      it("cannot be requested online") {
        val itemData = createSierraItemDataWith(
          fixedFields = Map(
            "79" -> createLocationWith("wgmem", "Medical Collection"),
            "88" -> createStatusWith("-", "Available"),
            "108" -> createOpacMsgWith("o", "Open shelves"),
          )
        )

        val (ac, _) = SierraItemAccess(
          location = Some(LocationType.OpenShelves),
          itemData = itemData
        )

        ac shouldBe AccessCondition(method = AccessMethod.OpenShelves)
      }

      it("gets a display note") {
        val itemData = createSierraItemDataWith(
          fixedFields = Map(
            "79" -> createLocationWith("wgpvm", "History of Medicine"),
            "88" -> createStatusWith("-", "Available"),
            "108" -> createOpacMsgWith("o", "Open shelves"),
          ),
          varFields = List(
            VarField(
              fieldTag = "n",
              content =
                "Shelved at the end of the Quick Ref. section with the oversize Quick Ref. books."
            )
          )
        )

        val (ac, _) = SierraItemAccess(
          location = Some(LocationType.OpenShelves),
          itemData = itemData
        )

        ac.note shouldBe Some(
          "Shelved at the end of the Quick Ref. section with the oversize Quick Ref. books.")
      }
    }

    it("is not available if it is missing") {
      val itemData = createSierraItemDataWith(
        fixedFields = Map(
          "79" -> createLocationWith("wgmem", "Medical Collection"),
          "88" -> createStatusWith("m", "Missing"),
          "108" -> createOpacMsgWith("o", "Open shelves"),
        )
      )

      val (ac, _) = SierraItemAccess(
        location = Some(LocationType.OpenShelves),
        itemData = itemData
      )

      ac shouldBe
        AccessCondition(
          method = AccessMethod.NotRequestable,
          status = Some(AccessStatus.Unavailable),
          note = Some("This item is missing.")
        )
    }

    it("is not available if it has a due date") {
      val itemData = createSierraItemDataWith(
        fixedFields = Map(
          "65" -> createFixedFieldWith("DUE DATE")("2020-09-01T03:00:00Z"),
          "79" -> createLocationWith("wgpvm", "History of Medicine"),
          "87" -> createFixedFieldWith("LOANRULE")("14"),
          "88" -> createStatusWith("-", "Available"),
          "108" -> createOpacMsgWith("o", "Open shelves")
        )
      )

      val (ac, _) = SierraItemAccess(
        location = Some(LocationType.OpenShelves),
        itemData = itemData
      )

      ac shouldBe AccessCondition(
        method = AccessMethod.OpenShelves,
        status = Some(AccessStatus.TemporarilyUnavailable),
        note = Some(
          "This item is temporarily unavailable. It is due for return on 1 September 2020."
        )
      )
    }
  }
  describe("an item on exhibition") {
    it("has a note based on its Reserves Note") {
      val displayreservation =
        "Locked filing cabinet, disused lavatory with a sign saying 'Beware of The Leopard'"
      val itemData = createSierraItemDataWith(
        fixedFields = Map(
          "79" -> createLocationWith("exres", "On Exhibition")
        ),
        varFields = List(
          VarField(fieldTag = "r", displayreservation)
        )
      )

      val (ac, _) = SierraItemAccess(
        location = Some(LocationType.OnExhibition),
        itemData = itemData
      )

      ac shouldBe AccessCondition(
        method = AccessMethod.NotRequestable,
        note = Some(displayreservation)
      )
    }
    it("can show multiple Reserves Notes") {
      val itemData = createSierraItemDataWith(
        fixedFields = Map(
          "79" -> createLocationWith("exres", "On Exhibition")
        ),
        varFields = List(
          VarField(fieldTag = "r", "in the bottom of a locked filing cabinet"),
          VarField(fieldTag = "r", "stuck in a disused lavatory"),
          VarField(
            fieldTag = "r",
            "with a sign on the door saying 'Beware of The Leopard'")
        ),
      )

      val (ac, _) = SierraItemAccess(
        location = Some(LocationType.OnExhibition),
        itemData = itemData
      )

      ac shouldBe AccessCondition(
        method = AccessMethod.NotRequestable,
        note = Some(
          "in the bottom of a locked filing cabinet<br />" +
            "stuck in a disused lavatory<br />" +
            "with a sign on the door saying 'Beware of The Leopard'"
        )
      )
    }
    it("Only shows substantive content from Reserves Notes") {
      val itemData = createSierraItemDataWith(
        fixedFields = Map(
          "79" -> createLocationWith("exres", "On Exhibition")
        ),
        varFields = List(
          VarField(
            fieldTag = "r",
            "25-12-22 ON RESERVE FOR Beware of the Leopard"),
          VarField(
            fieldTag = "r",
            "25-12-22 OFF RESERVE FOR Beware of the Leopard CIRCED 2 TIMES"),
          VarField(fieldTag = "r", "In a locked filing cabinet")
        )
      )

      val (ac, _) = SierraItemAccess(
        location = Some(LocationType.OnExhibition),
        itemData = itemData
      )
      // To avoid confusion for staff, and in the interest of completeness, notes that are identical
      // after removing the non-interesting content are preserved.
      // This scenario *should* not be encountered.
      //  - Something that is On Exhibition is not expected to have an off reserve entry.
      //  - Something that has both an on and an off reserve entry is not expected to be On Exhibition.
      // However, it is something that *could* happen.
      // If these duplicate lines are encountered in real life, we should get the record corrected in Sierra.
      ac shouldBe AccessCondition(
        method = AccessMethod.NotRequestable,
        note = Some(
          "Beware of the Leopard<br />" +
            "Beware of the Leopard<br />" +
            "In a locked filing cabinet"
        )
      )
    }
    it(
      "has the default 'contact the library' note if there are no Reserves Notes") {
      val itemData = createSierraItemDataWith(
        fixedFields = Map(
          "79" -> createLocationWith("exres", "On Exhibition")
        ),
        varFields = List(
          VarField(fieldTag = "p", "GBP850")
        )
      )

      val (ac, _) = SierraItemAccess(
        location = Some(LocationType.OnExhibition),
        itemData = itemData
      )

      ac shouldBe AccessCondition(
        method = AccessMethod.NotRequestable,
        note = Some(
          s"""This item cannot be requested online. Please contact <a href="mailto:library@wellcomecollection.org">library@wellcomecollection.org</a> for more information.""")
      )
    }
  }
  it("handles the case where we can't map the access data") {
    val itemData = createSierraItemDataWith(
      fixedFields = Map(
        "79" -> createLocationWith("scmac", "Closed stores Arch. & MSS"),
        "88" -> createStatusWith("?", "Unknown"),
        "108" -> createOpacMsgWith("f", "Online request"),
      )
    )

    val (ac, _) = SierraItemAccess(
      location = Some(LocationType.ClosedStores),
      itemData = itemData
    )

    ac shouldBe AccessCondition(
      method = AccessMethod.NotRequestable,
      note = Some(
        s"""This item cannot be requested online. Please contact <a href="mailto:library@wellcomecollection.org">library@wellcomecollection.org</a> for more information.""")
    )
  }
}
