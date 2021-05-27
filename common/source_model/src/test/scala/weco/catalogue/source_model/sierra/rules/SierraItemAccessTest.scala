package weco.catalogue.source_model.sierra.rules

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessStatus,
  ItemStatus,
  LocationType,
  PhysicalLocationType
}
import weco.catalogue.source_model.generators.SierraDataGenerators
import weco.catalogue.source_model.sierra.Implicits._
import weco.catalogue.source_model.sierra.marc.{FixedField, VarField}
import weco.catalogue.source_model.sierra.{
  SierraBibData,
  SierraBibNumber,
  SierraItemData,
  SierraItemNumber,
  SierraTransformable
}

import java.io.{BufferedReader, FileInputStream, InputStreamReader, PrintWriter}
import scala.util.{Failure, Success, Try}

class SierraItemAccessTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {
  ignore("assigns access conditions for all Sierra items") {
    // Note: this test is not meant to hang around long-term.  It's a test harness
    // that runs through every SierraTransformable instance, tries to assign some
    // access conditions, and counts how many it can't handle.
    //
    // Looking at the items that can't be assigned access conditions helps us
    // find what needs fixing in the data/transformer.
    val reader: Iterator[String] =
      new Iterator[String] {
        val reader =
          new BufferedReader(
            new InputStreamReader(new FileInputStream(
              "/Users/alexwlchan/desktop/sierra/out_trimmed6.json")))

        override def hasNext: Boolean = reader.ready
        override def next(): String = reader.readLine()
      }

    val bibItemPairs: Iterator[
      (SierraBibNumber, SierraBibData, SierraItemNumber, SierraItemData)] =
      reader
        .flatMap { json =>
          val t = fromJson[SierraTransformable](json).get

          t.maybeBibRecord match {
            case Some(bibRecord) =>
              val bibData = fromJson[SierraBibData](bibRecord.data).get
              t.itemRecords.values.toList.map { itemRecord =>
                val itemData = fromJson[SierraItemData](itemRecord.data).get
                (bibRecord.id, bibData, itemRecord.id, itemData)
              }

            case None => List()
          }
        }

    var handled = 0
    var unhandled = 0

    val log = new PrintWriter("/users/alexwlchan/desktop/sierra_item_access_out.txt")

    bibItemPairs
      .filterNot {
        case (_, bibData, _, itemData) =>
          bibData.suppressed | bibData.deleted | itemData.suppressed | itemData.deleted
      }
      .foreach {
        case (bibId, bibData, itemId, itemData) =>
          // Note: When we wire up these into the items/locations code, we'll pass
          // in these values rather than re-parse them, but this works well enough
          // for the test harness.
          val bibAccessStatus = SierraAccessStatus.forBib(bibId, bibData)
          val location: Option[PhysicalLocationType] = itemData.location
            .map { _.name }
            .flatMap { SierraPhysicalLocationType.fromName(itemId, _) }

          val ac = Try {
            SierraItemAccess(bibAccessStatus, location, itemData)
          }

          ac match {
            case Success(_) => handled += 1
            case Failure(err) =>
              log.write(s"${bibId.withCheckDigit} / ${itemId.withCheckDigit}\n")
              log.write(s"${bibData.varFields.filter(_.marcTag.contains("506"))}\n")
              log.write(s"location = ${itemData.location}, holdCount = ${itemData.holdCount}\n")

              val interestingFixedFields = itemData.fixedFields.filterNot {
                case (code, _) =>
                  Set(
                    "68",
                    "63",
                    "71",
                    "72",
                    "80",
                    "67",
                    "66",
                    "69",
                    "78",
                    "109",
                    "162",
                    "264",
                    "161",
                    "306",
                    "70",
                    "86",
                    "64",
                    "81",
                    "59",
                    "64",
                    "76",
                    "98",
                    "93",
                    "84",
                    "265",
                    "62",
                    "83",
                    "77",
                    "110",
                    "60",
                    "94",
                    "127",
                    "57",
                    "58",
                    "74",
                    "85"
                  ).contains(code)
              }
              log.write(s"interesting fixed fields: $interestingFixedFields\n")

              val noteFields = itemData.varFields.filter(_.fieldTag.contains("n"))
              log.write(s"notes = $noteFields\n")

              log.write(s"err = $err\n\n- - -\n\n")

              unhandled += 1
          }
      }

    log.flush()

    println(s"$handled handled, $unhandled unhandled")
  }

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
            bibStatus = None,
            location = Some(LocationType.ClosedStores),
            itemData = itemData
          )

          ac shouldBe Some(
            AccessCondition(
              status = Some(AccessStatus.TemporarilyUnavailable),
              terms = Some("At digitisation and temporarily unavailable")
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
}
