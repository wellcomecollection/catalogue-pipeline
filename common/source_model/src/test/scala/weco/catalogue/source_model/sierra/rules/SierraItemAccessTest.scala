package weco.catalogue.source_model.sierra.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessStatus,
  ItemStatus,
  LocationType
}
import weco.catalogue.source_model.generators.SierraDataGenerators
import weco.catalogue.source_model.sierra.SierraItemNumber
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(AccessCondition(terms = Some("Online request")))
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = Some(AccessStatus.Open),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              status = Some(AccessStatus.Open),
              terms = Some("Online request")))
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = Some(AccessStatus.Restricted),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              status = Some(AccessStatus.Restricted),
              terms = Some("Online request")))
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = Some(AccessStatus.Restricted),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              status = Some(AccessStatus.Open),
              terms = Some("Online request")))
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(AccessCondition(terms = Some("Manual request")))
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = None,
            location = None,
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(terms = Some("Please request top item.")))
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = None,
            location = None,
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(terms = Some("Please request top item.")))
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = Some(AccessStatus.Closed),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(AccessCondition(status = AccessStatus.Closed))
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = Some(AccessStatus.Closed),
            location = None,
            itemData = itemData
          )

          ac shouldBe Some(AccessCondition(status = AccessStatus.Closed))
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(AccessCondition(status = AccessStatus.Unavailable))
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              status = Some(AccessStatus.TemporarilyUnavailable),
              terms = Some("This item is being digitised and is currently unavailable.")
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
                fieldTag = Some("n"),
                content = Some("<p>This item is being digitised and is currently unavailable.")
              )
            )
          )

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              status = Some(AccessStatus.TemporarilyUnavailable),
              terms = Some("This item is being digitised and is currently unavailable.")
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = Some(AccessStatus.ByAppointment),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(status = AccessStatus.ByAppointment)
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = Some(AccessStatus.PermissionRequired),
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(status = AccessStatus.PermissionRequired)
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(status = AccessStatus.PermissionRequired)
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              status = Some(AccessStatus.Unavailable),
              terms = Some("This item is missing.")
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

          val (ac, itemStatus) = SierraItemAccess(
            id = itemId,
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              status = Some(AccessStatus.Unavailable),
              terms = Some("This item is withdrawn.")
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

        val (ac, itemStatus) = SierraItemAccess(
          id = itemId,
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe Some(
          AccessCondition(
            status = Some(AccessStatus.TemporarilyUnavailable),
            terms = Some(
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

        val (ac, itemStatus) = SierraItemAccess(
          id = itemId,
          bibStatus = None,
          location = Some(LocationType.ClosedStores),
          itemData = itemData
        )

        ac shouldBe Some(
          AccessCondition(
            status = Some(AccessStatus.TemporarilyUnavailable),
            terms = Some(
              "Item is in use by another reader. Please ask at Enquiry Desk.")
          )
        )
        itemStatus shouldBe ItemStatus.TemporarilyUnavailable
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

        val (ac, itemStatus) = SierraItemAccess(
          id = itemId,
          bibStatus = None,
          location = Some(LocationType.OpenShelves),
          itemData = itemData
        )

        ac shouldBe None
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
              fieldTag = Some("n"),
              content = Some(
                "Shelved at the end of the Quick Ref. section with the oversize Quick Ref. books.")
            )
          )
        )

        val (ac, itemStatus) = SierraItemAccess(
          id = itemId,
          bibStatus = None,
          location = Some(LocationType.OpenShelves),
          itemData = itemData
        )

        ac shouldBe Some(
          AccessCondition(
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

      val (ac, itemStatus) = SierraItemAccess(
        id = itemId,
        bibStatus = None,
        location = Some(LocationType.OpenShelves),
        itemData = itemData
      )

      ac shouldBe Some(
        AccessCondition(
          status = Some(AccessStatus.Unavailable),
          terms = Some("This item is missing.")
        )
      )
      itemStatus shouldBe ItemStatus.Unavailable
    }
  }

  val itemId: SierraItemNumber = createSierraItemNumber
}
