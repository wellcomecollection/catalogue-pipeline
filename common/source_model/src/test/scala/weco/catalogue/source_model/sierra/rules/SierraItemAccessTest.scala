package weco.catalogue.source_model.sierra.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  ItemStatus,
  LocationType
}
import weco.catalogue.source_model.generators.SierraDataGenerators
import weco.catalogue.source_model.sierra.identifiers.{
  SierraBibNumber,
  SierraItemNumber
}
import weco.catalogue.source_model.sierra.marc.{FixedField, VarField}

class SierraItemAccessTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {
  describe("an item in the closed stores") {
    describe("with no holds") {
      describe("can be requested online") {
        it("if it has no restrictions") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "scmac",
                display = "Closed stores Arch. & MSS"),
              "88" -> FixedField(
                label = "STATUS",
                value = "-",
                display = "Available"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "f",
                display = "Online request"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(AccessCondition(method = AccessMethod.OnlineRequest))
          itemStatus shouldBe ItemStatus.Available
        }

        it("if it has no restrictions and the bib is open") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "scmwf",
                display = "Closed stores A&MSS Well.Found."),
              "88" -> FixedField(
                label = "STATUS",
                value = "-",
                display = "Available"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "f",
                display = "Online request"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = Some(AccessStatus.Open),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.OnlineRequest,
              status = AccessStatus.Open))
          itemStatus shouldBe ItemStatus.Available
        }

        it("if it's restricted") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "scmac",
                display = "Closed stores Arch. & MSS"),
              "88" -> FixedField(
                label = "STATUS",
                value = "6",
                display = "Restricted"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "f",
                display = "Online request"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = Some(AccessStatus.Restricted),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.OnlineRequest,
              status = AccessStatus.Restricted))
          itemStatus shouldBe ItemStatus.Available
        }

        it("if the bib is restricted but the item is open") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "scmac",
                display = "Closed stores Arch. & MSS"),
              "88" -> FixedField(
                label = "STATUS",
                value = "-",
                display = "Available"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "f",
                display = "Online request"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = Some(AccessStatus.Restricted),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.OnlineRequest,
              status = AccessStatus.Open))
          itemStatus shouldBe ItemStatus.Available
        }
      }

      describe("cannot be requested") {
        it("if it needs a manual request") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "61" -> FixedField(
                label = "I TYPE",
                value = "4",
                display = "serial"),
              "79" -> FixedField(
                label = "LOCATION",
                value = "sgser",
                display = "Closed stores journals"),
              "88" -> FixedField(
                label = "STATUS",
                value = "-",
                display = "Available"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "n",
                display = "Manual request"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(AccessCondition(method = AccessMethod.ManualRequest))
          itemStatus shouldBe ItemStatus.Available
        }

        it("if it's bound in the top item") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "bwith",
                display = "bound in above"),
              "88" -> FixedField(
                label = "STATUS",
                value = "b",
                display = "As above"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "-",
                display = "-"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = None,
            location = None,
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.NotRequestable,
              terms = Some("Please request top item.")))
          itemStatus shouldBe ItemStatus.Unavailable
        }

        it("if it's contained the top item") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "cwith",
                display = "contained in above"),
              "88" -> FixedField(
                label = "STATUS",
                value = "c",
                display = "As above"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "-",
                display = "-"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = None,
            location = None,
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.NotRequestable,
              terms = Some("Please request top item.")))
          itemStatus shouldBe ItemStatus.Unavailable
        }

        it("if the bib and the item are closed") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "sc#ac",
                display = "Unrequestable Arch. & MSS"),
              "88" -> FixedField(
                label = "STATUS",
                value = "h",
                display = "Closed"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "u",
                display = "Unavailable"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = Some(AccessStatus.Closed),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = AccessStatus.Closed
            ))
          itemStatus shouldBe ItemStatus.Unavailable
        }

        it("if the bib and the item are closed, and there's no location") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "sc#ac",
                display = "Unrequestable Arch. & MSS"),
              "88" -> FixedField(
                label = "STATUS",
                value = "h",
                display = "Closed"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "u",
                display = "Unavailable"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = Some(AccessStatus.Closed),
            location = None,
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = AccessStatus.Closed))
          itemStatus shouldBe ItemStatus.Unavailable
        }

        it("if the item is unavailable") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "sgser",
                display = "Closed stores journals"),
              "88" -> FixedField(
                label = "STATUS",
                value = "r",
                display = "Unavailable"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "u",
                display = "Unavailable"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = AccessStatus.Unavailable))
          itemStatus shouldBe ItemStatus.Unavailable
        }

        it("if the item is at digitisation") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "sgser",
                display = "Closed stores journals"),
              "88" -> FixedField(
                label = "STATUS",
                value = "r",
                display = "Unavailable"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "b",
                display = "@ digitisation"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = Some(AccessStatus.TemporarilyUnavailable),
              note = Some(
                "This item is being digitised and is currently unavailable.")
            )
          )
          itemStatus shouldBe ItemStatus.TemporarilyUnavailable
        }

        it("if doesn't double up the note about digitisation") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "sgser",
                display = "Closed stores journals"),
              "88" -> FixedField(
                label = "STATUS",
                value = "r",
                display = "Unavailable"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "b",
                display = "@ digitisation"),
            ),
            varFields = List(
              VarField(
                fieldTag = "n",
                content =
                  "<p>This item is being digitised and is currently unavailable."
              )
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = Some(AccessStatus.TemporarilyUnavailable),
              note = Some(
                "This item is being digitised and is currently unavailable.")
            )
          )
          itemStatus shouldBe ItemStatus.TemporarilyUnavailable
        }

        it("if the bib and item are by appointment") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "scmac",
                display = "Closed stores Arch. & MSS"),
              "88" -> FixedField(
                label = "STATUS",
                value = "y",
                display = "Permission required"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "a",
                display = "By appointment"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = Some(AccessStatus.ByAppointment),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.ManualRequest,
              status = AccessStatus.ByAppointment)
          )
          itemStatus shouldBe ItemStatus.Available
        }

        it("if the bib and item need donor permission") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "sc#ac",
                display = "Unrequestable Arch. & MSS"),
              "88" -> FixedField(
                label = "STATUS",
                value = "y",
                display = "Permission required"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "q",
                display = "Donor permission"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = Some(AccessStatus.PermissionRequired),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.ManualRequest,
              status = AccessStatus.PermissionRequired)
          )
          itemStatus shouldBe ItemStatus.Available
        }

        it("if the bib and item needs donor permission") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "sicon",
                display = "Closed stores Visual"),
              "88" -> FixedField(
                label = "STATUS",
                value = "y",
                display = "Permission required"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "q",
                display = "Donor permission"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.ManualRequest,
              status = AccessStatus.PermissionRequired)
          )
          itemStatus shouldBe ItemStatus.Available
        }

        it("if the item is missing") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "sghi2",
                display = "Closed stores Hist. 2"),
              "88" -> FixedField(
                label = "STATUS",
                value = "m",
                display = "Missing"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "f",
                display = "Online request"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = Some(AccessStatus.Unavailable),
              note = Some("This item is missing.")
            )
          )
          itemStatus shouldBe ItemStatus.Unavailable
        }

        it("if the item is withdrawn") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "sghx2",
                display = "Closed stores Hist. O/S 2"),
              "88" -> FixedField(
                label = "STATUS",
                value = "x",
                display = "Withdrawn"),
              "108" -> FixedField(
                label = "OPACMSG",
                value = "u",
                display = "Unavailable"),
            )
          )

          val (ac, _, itemStatus) = SierraItemAccess(
            bibId = bibId,
            itemId = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = Some(AccessStatus.Unavailable),
              note = Some("This item is withdrawn.")
            )
          )
          itemStatus shouldBe ItemStatus.Unavailable
        }
      }
    }

    describe("that's on hold") {
      it("can't be requested when another reader has placed a hold") {
        val itemData = createSierraItemDataWith(
          holdCount = Some(1),
          fixedFields = Map(
            "79" -> FixedField(
              label = "LOCATION",
              value = "sgeph",
              display = "Closed stores ephemera"),
            "88" -> FixedField(
              label = "STATUS",
              value = "-",
              display = "Available"),
            "108" -> FixedField(
              label = "OPACMSG",
              value = "f",
              display = "Online request"),
          )
        )

        val (ac, _, itemStatus) = SierraItemAccess(
          bibId = bibId,
          itemId = itemId,
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe Some(
          AccessCondition(
            method = AccessMethod.ManualRequest,
            status = Some(AccessStatus.TemporarilyUnavailable),
            note = Some(
              "Item is in use by another reader. Please ask at Enquiry Desk.")
          )
        )
        itemStatus shouldBe ItemStatus.TemporarilyUnavailable
      }

      it("can't be requested when it's on the hold shelf for another reader") {
        val itemData = createSierraItemDataWith(
          holdCount = Some(1),
          fixedFields = Map(
            "79" -> FixedField(
              label = "LOCATION",
              value = "swms4",
              display = "Closed stores WMS 4"),
            "88" -> FixedField(
              label = "STATUS",
              value = "!",
              display = "On holdshelf"),
            "108" -> FixedField(
              label = "OPACMSG",
              value = "f",
              display = "Online request"),
          )
        )

        val (ac, _, itemStatus) = SierraItemAccess(
          bibId = bibId,
          itemId = itemId,
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe Some(
          AccessCondition(
            method = AccessMethod.ManualRequest,
            status = Some(AccessStatus.TemporarilyUnavailable),
            note = Some(
              "Item is in use by another reader. Please ask at Enquiry Desk.")
          )
        )
        itemStatus shouldBe ItemStatus.TemporarilyUnavailable
      }
    }

    describe("gets the right note") {
      it("if there's a display note about manual requesting") {
        val itemData = createSierraItemDataWith(
          fixedFields = Map(
            "61" -> FixedField(
              label = "I TYPE",
              value = "4",
              display = "serial"),
            "79" -> FixedField(
              label = "LOCATION",
              value = "sgser",
              display = "Closed stores journals"),
            "88" -> FixedField(
              label = "STATUS",
              value = "-",
              display = "Available"),
            "108" -> FixedField(
              label = "OPACMSG",
              value = "n",
              display = "Manual request"),
          ),
          varFields = List(
            VarField(
              fieldTag = "n",
              content = "Email library@wellcomecollection.org to tell us why you need access. We’ll reply within a week."
            )
          )
        )

        val (Some(ac), note, itemStatus) = SierraItemAccess(
          bibId = bibId,
          itemId = itemId,
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe AccessCondition(
          method = AccessMethod.ManualRequest,
          note = Some("Email library@wellcomecollection.org to tell us why you need access. We’ll reply within a week.")
        )
        note shouldBe None
        itemStatus shouldBe ItemStatus.Available
      }

      it("doesn't overwrite the note if there's a hold on the item") {
        val itemData = createSierraItemDataWith(
          fixedFields = Map(
            "79" -> FixedField(
              label = "LOCATION",
              value = "sgser",
              display = "Closed stores journals"),
            "88" -> FixedField(
              label = "STATUS",
              value = "-",
              display = "Available"),
            "108" -> FixedField(
              label = "OPACMSG",
              value = "f",
              display = "Online request"),
          ),
          holdCount = Some(1),
          varFields = List(
            VarField(
              fieldTag = "n",
              content = "Email library@wellcomecollection.org to tell us why you need access. We’ll reply within a week."
            )
          )
        )

        val (Some(ac), note, _) = SierraItemAccess(
          bibId = bibId,
          itemId = itemId,
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac.note shouldBe Some("Item is in use by another reader. Please ask at Enquiry Desk.")
        note shouldBe None
      }

      it("if there's a display note with access information") {
        val itemData = createSierraItemDataWith(
          fixedFields = Map(
            "61" -> FixedField(
              label = "I TYPE",
              value = "4",
              display = "serial"
            ),
            "79" -> FixedField(
              label = "LOCATION",
              value = "hgser",
              display = "Offsite"),
            "88" -> FixedField(
              label = "STATUS",
              value = "y",
              display = "Permission required"),
            "108" -> FixedField(
              label = "OPACMSG",
              value = "a",
              display = "By appointment"),
          ),
          varFields = List(
            VarField(
              fieldTag = "n",
              content =
                "Email library@wellcomecollection.org to tell us why you need the physical copy. We'll reply within a week."
            )
          )
        )

        val (Some(ac), _, _) = SierraItemAccess(
          bibId = bibId,
          itemId = itemId,
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac.note shouldBe Some(
          "Email library@wellcomecollection.org to tell us why you need the physical copy. We'll reply within a week.")
        ac.terms shouldBe None
      }

      it("returns the note if it's unrelated to access data") {
        val itemData = createSierraItemDataWith(
          fixedFields = Map(
            "79" -> FixedField(
              label = "LOCATION",
              value = "scmac",
              display = "Closed stores Arch. & MSS"),
            "88" -> FixedField(
              label = "STATUS",
              value = "-",
              display = "Available"),
            "108" -> FixedField(
              label = "OPACMSG",
              value = "f",
              display = "Online request"),
          ),
          varFields = List(
            VarField(
              fieldTag = "n",
              content = "uncoloured impression on paper mount"
            )
          )
        )

        val (_, Some(note), _) = SierraItemAccess(
          bibId = bibId,
          itemId = itemId,
          bibStatus = None,
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
            "79" -> FixedField(
              label = "LOCATION",
              value = "wgmem",
              display = "Medical Collection"),
            "88" -> FixedField(
              label = "STATUS",
              value = "-",
              display = "Available"),
            "108" -> FixedField(
              label = "OPACMSG",
              value = "o",
              display = "Open shelves"),
          )
        )

        val (Some(ac), _, itemStatus) = SierraItemAccess(
          bibId = bibId,
          itemId = itemId,
          bibStatus = None,
          location = Some(LocationType.OpenShelves),
          itemData = itemData
        )

        ac shouldBe AccessCondition(method = AccessMethod.OpenShelves)
        itemStatus shouldBe ItemStatus.Available
      }

      it("gets a display note") {
        val itemData = createSierraItemDataWith(
          fixedFields = Map(
            "79" -> FixedField(
              label = "LOCATION",
              value = "wgpvm",
              display = "History of Medicine"),
            "88" -> FixedField(
              label = "STATUS",
              value = "-",
              display = "Available"),
            "108" -> FixedField(
              label = "OPACMSG",
              value = "o",
              display = "Open shelves"),
          ),
          varFields = List(
            VarField(
              fieldTag = "n",
              content =
                "Shelved at the end of the Quick Ref. section with the oversize Quick Ref. books."
            )
          )
        )

        val (ac, _, itemStatus) = SierraItemAccess(
          bibId = bibId,
          itemId = itemId,
          bibStatus = None,
          location = Some(LocationType.OpenShelves),
          itemData = itemData
        )

        ac shouldBe Some(
          AccessCondition(
            method = AccessMethod.OpenShelves,
            note = Some(
              "Shelved at the end of the Quick Ref. section with the oversize Quick Ref. books.")
          )
        )
        itemStatus shouldBe ItemStatus.Available
      }
    }

    it("is not available if it is missing") {
      val itemData = createSierraItemDataWith(
        fixedFields = Map(
          "79" -> FixedField(
            label = "LOCATION",
            value = "wgmem",
            display = "Medical Collection"),
          "88" -> FixedField(
            label = "STATUS",
            value = "m",
            display = "Missing"),
          "108" -> FixedField(
            label = "OPACMSG",
            value = "o",
            display = "Open shelves"),
        )
      )

      val (ac, _, itemStatus) = SierraItemAccess(
        bibId = bibId,
        itemId = itemId,
        bibStatus = None,
        location = Some(LocationType.OpenShelves),
        itemData = itemData
      )

      ac shouldBe Some(
        AccessCondition(
          method = AccessMethod.NotRequestable,
          status = Some(AccessStatus.Unavailable),
          note = Some("This item is missing.")
        )
      )
      itemStatus shouldBe ItemStatus.Unavailable
    }
  }

  it("handles the case where we can't map the access data") {
    val itemData = createSierraItemDataWith(
      fixedFields = Map(
        "79" -> FixedField(
          label = "LOCATION",
          value = "scmac",
          display = "Closed stores Arch. & MSS"),
        "88" -> FixedField(
          label = "STATUS",
          value = "?",
          display = "Unknown"),
        "108" -> FixedField(
          label = "OPACMSG",
          value = "f",
          display = "Online request"),
      )
    )

    val (Some(ac), _, _) = SierraItemAccess(
      bibId = bibId,
      itemId = itemId,
      bibStatus = None,
      location = Some(LocationType.ClosedStores),
      itemData = itemData
    )

    ac shouldBe AccessCondition(
      method = AccessMethod.NotRequestable,
      note = Some(
        s"""Please check this item <a href="https://search.wellcomelibrary.org/iii/encore/record/C__Rb${bibId.withoutCheckDigit}?lang=eng">on the Wellcome Library website</a> for access information""")
    )
  }

  val bibId: SierraBibNumber = createSierraBibNumber
  val itemId: SierraItemNumber = createSierraItemNumber
}
