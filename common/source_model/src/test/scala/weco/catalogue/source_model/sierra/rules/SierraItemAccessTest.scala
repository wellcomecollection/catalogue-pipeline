package weco.catalogue.source_model.sierra.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  LocationType,
  PhysicalLocationType
}
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.data.SierraItemData
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.{FixedField, VarField}

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

          val (ac, _) = getItemAccess(
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe AccessCondition(
            method = AccessMethod.OnlineRequest,
            status = AccessStatus.Open)
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

          val (ac, _) = getItemAccess(
            bibStatus = Some(AccessStatus.Open),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.OnlineRequest,
              status = AccessStatus.Open)
        }

        it("if it has no restrictions and the bib is open with advisory") {
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

          val (ac, _) = getItemAccess(
            bibStatus = Some(AccessStatus.OpenWithAdvisory),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.OnlineRequest,
              status = AccessStatus.OpenWithAdvisory)
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

          val (ac, _) = getItemAccess(
            bibStatus = Some(AccessStatus.Restricted),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.OnlineRequest,
              status = AccessStatus.Restricted)
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

          val (ac, _) = getItemAccess(
            bibStatus = Some(AccessStatus.Restricted),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.OnlineRequest,
              status = AccessStatus.Open)
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

          val (ac, _) = getItemAccess(
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe AccessCondition(method = AccessMethod.ManualRequest)
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

          val (ac, _) = getItemAccess(
            bibStatus = None,
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

          val (ac, _) = getItemAccess(
            bibStatus = None,
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

          val (ac, _) = getItemAccess(
            bibStatus = Some(AccessStatus.Closed),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = AccessStatus.Closed
            )
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

          val (ac, _) = getItemAccess(
            bibStatus = Some(AccessStatus.Closed),
            location = None,
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = AccessStatus.Closed)
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

          val (ac, _) = getItemAccess(
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = AccessStatus.Unavailable)
        }

        it("if the item is unavailable and the bib is temporarily unavailable") {
          val itemData = createSierraItemDataWith(
            fixedFields = Map(
              "79" -> FixedField(
                label = "LOCATION",
                value = "sc#ac",
                display = "Unrequestable Arch. & MSS"),
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

          val (ac, _) = getItemAccess(
            bibStatus = Some(AccessStatus.TemporarilyUnavailable),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.NotRequestable,
              status = AccessStatus.Unavailable)
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

          val (ac, _) = getItemAccess(
            bibStatus = None,
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

          val (ac, _) = getItemAccess(
            bibStatus = None,
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

          val (ac, _) = getItemAccess(
            bibStatus = Some(AccessStatus.ByAppointment),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.ManualRequest,
              status = AccessStatus.ByAppointment)
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

          val (ac, _) = getItemAccess(
            bibStatus = Some(AccessStatus.PermissionRequired),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe
            AccessCondition(
              method = AccessMethod.ManualRequest,
              status = AccessStatus.PermissionRequired)
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

          val (ac, _) = getItemAccess(
            bibStatus = None,
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

          val (ac, _) = getItemAccess(
            bibStatus = None,
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

          val (ac, _) = getItemAccess(
            bibStatus = None,
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

        val (ac, _) = getItemAccess(
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe
          AccessCondition(
            method = AccessMethod.NotRequestable,
            status = Some(AccessStatus.TemporarilyUnavailable),
            note = Some(
              "Item is in use by another reader. Please ask at Enquiry Desk.")
          )
      }

      it("when an item is on hold for a loan rule") {
        val itemData = createSierraItemDataWith(
          holdCount = Some(1),
          fixedFields = Map(
            "79" -> FixedField(
              label = "LOCATION",
              value = "sgeph",
              display = "Closed stores ephemera"),
            "87" -> FixedField(label = "LOANRULE", value = "5"),
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

        val (ac, _) = getItemAccess(
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe
          AccessCondition(
            method = AccessMethod.NotRequestable,
            status = Some(AccessStatus.TemporarilyUnavailable),
            note = Some(
              "Item is in use by another reader. Please ask at Enquiry Desk.")
          )
      }

      it("when a manual request item is on hold for somebody else") {
        val itemData = createSierraItemDataWith(
          holdCount = Some(1),
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

        val (ac, _) = getItemAccess(
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe
          AccessCondition(
            method = AccessMethod.NotRequestable,
            status = Some(AccessStatus.TemporarilyUnavailable),
            note = Some(
              "Item is in use by another reader. Please ask at Enquiry Desk.")
          )
      }

      it("when it's on the hold shelf for another reader") {
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

        val (ac, _) = getItemAccess(
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe
          AccessCondition(
            method = AccessMethod.NotRequestable,
            status = Some(AccessStatus.TemporarilyUnavailable),
            note = Some(
              "Item is in use by another reader. Please ask at Enquiry Desk.")
          )
      }

      it("if there's a status on the bib") {
        // This is based on b32204887 / i19379778, as retrieved 13 August 2021
        val itemData = createSierraItemDataWith(
          holdCount = Some(1),
          fixedFields = Map(
            "79" -> FixedField(
              label = "scmac",
              value = "swms4",
              display = "Closed stores Arch. & MSS"),
            "88" -> FixedField(
              label = "STATUS",
              value = "!",
              display = "On holdshelf"),
            "108" -> FixedField(
              label = "OPACMSG",
              value = "a",
              display = "By appointment"),
          )
        )

        val (ac, _) = getItemAccess(
          bibStatus = Some(AccessStatus.ByAppointment),
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe
          AccessCondition(
            method = AccessMethod.NotRequestable,
            status = Some(AccessStatus.TemporarilyUnavailable),
            note = Some(
              "Item is in use by another reader. Please ask at Enquiry Desk.")
          )
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
              content =
                "Email library@wellcomecollection.org to tell us why you need access. We’ll reply within a week."
            )
          )
        )

        val (ac, note) = getItemAccess(
          bibStatus = None,
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
              content =
                "Email library@wellcomecollection.org to tell us why you need access. We’ll reply within a week."
            )
          )
        )

        val (ac, note) = getItemAccess(
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac.note shouldBe Some(
          "Item is in use by another reader. Please ask at Enquiry Desk.")
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

        val (ac, _) = getItemAccess(
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac.note shouldBe Some(
          "Email library@wellcomecollection.org to tell us why you need the physical copy. We'll reply within a week.")
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

        val (_, Some(note)) = getItemAccess(
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

        val (ac, _) = getItemAccess(
          bibStatus = None,
          location = Some(LocationType.OpenShelves),
          itemData = itemData
        )

        ac shouldBe AccessCondition(method = AccessMethod.OpenShelves)
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

        val (ac, _) = getItemAccess(
          bibStatus = None,
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

      val (ac, _) = getItemAccess(
        bibStatus = None,
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
  }

  it("handles the case where we can't map the access data") {
    val bibId = createSierraBibNumber
    val itemData = createSierraItemDataWith(
      fixedFields = Map(
        "79" -> FixedField(
          label = "LOCATION",
          value = "scmac",
          display = "Closed stores Arch. & MSS"),
        "88" -> FixedField(label = "STATUS", value = "?", display = "Unknown"),
        "108" -> FixedField(
          label = "OPACMSG",
          value = "f",
          display = "Online request"),
      )
    )

    val (ac, _) = getItemAccess(
      bibId = bibId,
      bibStatus = None,
      location = Some(LocationType.ClosedStores),
      itemData = itemData
    )

    ac shouldBe AccessCondition(
      method = AccessMethod.NotRequestable,
      note = Some(
        s"""This item cannot be requested online. Please contact <a href="mailto:library@wellcomecollection.org">library@wellcomecollection.org</a> for more information.""")
    )
  }

  private def getItemAccess(
    bibId: SierraBibNumber = createSierraBibNumber,
    bibStatus: Option[AccessStatus],
    location: Option[PhysicalLocationType],
    itemData: SierraItemData
  ): (AccessCondition, Option[String]) =
    SierraItemAccess(
      bibId = bibId,
      bibStatus = bibStatus,
      location = location,
      itemData = itemData
    )
}
