package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import uk.ac.wellcome.json.JsonUtil._
import weco.catalogue.internal_model.locations.{AccessCondition, AccessStatus}
import weco.catalogue.source_model.generators.SierraDataGenerators
import weco.catalogue.source_model.sierra.Implicits._
import weco.catalogue.source_model.sierra._
import weco.catalogue.source_model.sierra.marc.{FixedField, MarcSubfield, VarField}
import weco.catalogue.source_model.sierra.source.SierraSourceLocation

import java.io.{BufferedReader, FileInputStream, InputStreamReader}
import scala.util.{Failure, Success, Try}

class SierraAccessConditionTest extends AnyFunSpec with Matchers with SierraDataGenerators with TableDrivenPropertyChecks {
  it("assigns access conditions for all Sierra items") {
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
            new InputStreamReader(
              new FileInputStream("/Users/alexwlchan/desktop/sierra/out.json")))

        override def hasNext: Boolean = reader.ready
        override def next(): String = reader.readLine()
      }

    val bibItemPairs: Iterator[(SierraBibNumber, SierraBibData, SierraItemNumber, SierraItemData)] =
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

    bibItemPairs
      .filterNot {
        case (_, bibData, _, itemData) =>
          bibData.suppressed | bibData.deleted | itemData.suppressed | itemData.deleted
      }
      .foreach { case (bibId, bibData, itemId, itemData) =>
        val ac = Try { SierraAccessCondition(bibId, bibData, itemId, itemData) }

        // Print the bib/item data for the first 100 failures
        if (unhandled < 100) {
          println(bibId.withCheckDigit)
          println(bibData.varFields.filter(_.marcTag.contains("506")))
          println(itemId.withCheckDigit)
          println(itemData.location)
          println(itemData.fixedFields.filterNot { case (code, _) => Set("68", "63", "71",  "72", "80", "67", "66", "69", "78", "109", "162", "264", "161", "306", "70", "86", "64", "81", "59", "64", "76", "98", "93", "84", "265", "62", "83", "77", "110", "60", "94", "127", "57", "58", "74", "85").contains(code) })
          println(itemData.varFields.filter(_.fieldTag.contains("n")))
          println("")
        }

        ac match {
          case Success(_) => handled += 1
          case Failure(_) => unhandled += 1
        }
      }

    println(s"$handled handled, $unhandled unhandled")
  }

  describe("if an item is on the open shelves") {
    describe("and does not have any holds") {
      it("if it is available, then it has no access conditions") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "wgmem", display = "Medical Collection"),
            "88" -> FixedField(label = "STATUS", value = "-", display = "Available"),
            "108" -> FixedField(label = "OPACMSG", value = "o", display = "Open shelves"),
          ),
          location = Some(SierraSourceLocation(code = "wgmem", name = "Medical Collection"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe empty
        status shouldBe ItemStatus.Available
      }

      it("if it is at digitisation, then it is temporarily unavailable") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "wgser", display = "Journals"),
            "88" -> FixedField(label = "STATUS", value = "r", display = "Unavailable"),
            "108" -> FixedField(label = "OPACMSG", value = "b", display = "@ digitisation"),
          ),
          location = Some(SierraSourceLocation(code = "wgser", name = "Journals"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(
            status = Some(AccessStatus.TemporarilyUnavailable), terms = Some("At digitisation and temporarily unavailable.")
          )
        )
        status shouldBe ItemStatus.TemporarilyUnavailable
      }
    }
  }

  describe("if an item is in the closed stores") {
    describe("and does not have any holds") {
      it("if it is available, then it can be requested online") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "scmac", display = "Closed stores Arch. & MSS"),
            "88" -> FixedField(label = "STATUS", value = "-", display = "Available"),
            "108" -> FixedField(label = "OPACMSG", value = "f", display = "Online request"),
          ),
          location = Some(SierraSourceLocation(code = "scmac", name = "Closed stores Arch. & MSS")),
          varFields = List(
            VarField(
              fieldTag = Some("n"),
              content = Some("Mount bears watermark: B M FABRIANO")
            )
          )
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(terms = Some("Online request"), note = Some("Mount bears watermark: B M FABRIANO"))
        )
        status shouldBe ItemStatus.Available
      }

      it("if it is available and the bib is open, then it can be requested online") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibDataWith(
          varFields = List(
            VarField(
              marcTag = Some("506"),
              subfields = List(
                MarcSubfield(tag = "f", content = "Open.")
              )
            )
          )
        )

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "scmac", display = "Closed stores Arch. & MSS"),
            "88" -> FixedField(label = "STATUS", value = "-", display = "Available"),
            "108" -> FixedField(label = "OPACMSG", value = "f", display = "Online request"),
          ),
          location = Some(SierraSourceLocation(code = "scmac", name = "Closed stores Arch. & MSS"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = Some(AccessStatus.Open), terms = Some("Online request"))
        )
        status shouldBe ItemStatus.Available
      }

      it("if the bib is by appointment, then the item cannot be requested online") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibDataWith(
          varFields = List(
            VarField(
              marcTag = Some("506"),
              subfields = List(
                MarcSubfield(tag = "f", content = "By Appointment.")
              )
            )
          )
        )

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "scmac", display = "Closed stores Arch. & MSS"),
            "88" -> FixedField(label = "STATUS", value = "y", display = "Permission required"),
            "108" -> FixedField(label = "OPACMSG", value = "a", display = "By appointment"),
          ),
          location = Some(SierraSourceLocation(code = "scmac", name = "Closed stores Arch. & MSS")),
          varFields = List(
            VarField(
              fieldTag = Some("n"),
              content = Some("Offsite")
            )
          )
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = Some(AccessStatus.ByAppointment), note = Some("Offsite"))
        )
        status shouldBe ItemStatus.Available
      }

      it("if the bib is permission required but the item is by appointment, then the item is by appointment") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibDataWith(
          varFields = List(
            VarField(
              marcTag = Some("506"),
              subfields = List(
                MarcSubfield(tag = "f", content = "Donor permission.")
              )
            )
          )
        )

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "sepin", display = "Closed stores EPB Incunabula"),
            "88" -> FixedField(label = "STATUS", value = "y", display = "Permission required"),
            "108" -> FixedField(label = "OPACMSG", value = "a", display = "By appointment"),
          ),
          location = Some(SierraSourceLocation(code = "sepin", name = "Closed stores EPB Incunabula"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = AccessStatus.ByAppointment)
        )
        status shouldBe ItemStatus.Available
      }

      it("if the item is by appointment, then it cannot be requested online") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "sepbr", display = "Closed stores EPB Planchests"),
            "88" -> FixedField(label = "STATUS", value = "y", display = "Permission required"),
            "108" -> FixedField(label = "OPACMSG", value = "a", display = "By appointment"),
          ),
          location = Some(SierraSourceLocation(code = "sepbr", name = "Closed stores EPB Planchests"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = Some(AccessStatus.ByAppointment))
        )
        status shouldBe ItemStatus.Available
      }

      it("and needs a manual request") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "61" -> FixedField(label = "I TYPE", value = "4", display = "serial"),
            "79" -> FixedField(label = "LOCATION", value = "sgser", display = "Closed stores journals"),
            "88" -> FixedField(label = "STATUS", value = "-", display = "Available"),
            "108" -> FixedField(label = "OPACMSG", value = "n", display = "Manual request"),
          ),
          location = Some(SierraSourceLocation(code = "sgser", name = "Closed stores journals"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(
            terms = Some("Please complete a manual request slip.  This item cannot be requested online.")
          )
        )
        status shouldBe ItemStatus.Available
      }

      it("you need to ask at the enquiry desk") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "61" -> FixedField(label = "I TYPE", value = "4", display = "serial"),
            "79" -> FixedField(label = "LOCATION", value = "sgser", display = "Offsite"),
            "88" -> FixedField(label = "STATUS", value = "-", display = "Available"),
            "108" -> FixedField(label = "OPACMSG", value = "i", display = "Ask at desk"),
          ),
          location = Some(SierraSourceLocation(code = "sgser", name = "Offsite"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(
            terms = Some("Please complete a manual request slip.  This item cannot be requested online.")
          )
        )
        status shouldBe ItemStatus.Available
      }

      it("and is missing") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "sghi2", display = "Closed stores Hist. 2"),
            "88" -> FixedField(label = "STATUS", value = "m", display = "Missing"),
            "108" -> FixedField(label = "OPACMSG", value = "f", display = "Online request"),
          ),
          location = Some(SierraSourceLocation(code = "sghi2", name = "Closed stores Hist. 2"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(
            status = Some(AccessStatus.Unavailable),
            terms = Some("This item is missing.")
          )
        )
        status shouldBe ItemStatus.Unavailable
      }

      it("and is at digitisation") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "sgser", display = "Closed stores journals"),
            "88" -> FixedField(label = "STATUS", value = "r", display = "Unavailable"),
            "108" -> FixedField(label = "OPACMSG", value = "b", display = "@ digitisation"),
          ),
          location = Some(SierraSourceLocation(code = "sgser", name = "Closed stores journals"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(
            status = Some(AccessStatus.TemporarilyUnavailable),
            terms = Some("At digitisation and temporarily unavailable.")
          )
        )
        status shouldBe ItemStatus.TemporarilyUnavailable
      }

      it("if it requires permission and so does the bib") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibDataWith(
          varFields = List(
            VarField(
              marcTag = Some("506"),
              subfields = List(
                MarcSubfield(tag = "f", content = "Donor Permission.")
              )
            )
          )
        )

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "sc#ac", display = "Unrequestable Arch. & MSS"),
            "88" -> FixedField(label = "STATUS", value = "y", display = "Permission required"),
            "108" -> FixedField(label = "OPACMSG", value = "q", display = "Donor permission"),
          ),
          location = Some(SierraSourceLocation(code = "sc#ac", name = "Unrequestable Arch. & MSS"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = AccessStatus.PermissionRequired)
        )
        status shouldBe ItemStatus.Available
      }

      it("if the bib and item are both closed, then the item cannot be requested") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibDataWith(
          varFields = List(
            VarField(
              marcTag = Some("506"),
              subfields = List(
                MarcSubfield(tag = "f", content = "Closed.")
              )
            )
          )
        )

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "sc#ac", display = "Unrequestable Arch. & MSS"),
            "88" -> FixedField(label = "STATUS", value = "h", display = "Closed"),
            "108" -> FixedField(label = "OPACMSG", value = "u", display = "Unavailable"),
          ),
          location = Some(SierraSourceLocation(code = "sc#ac", name = "Unrequestable Arch. & MSS"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = AccessStatus.Closed)
        )
        status shouldBe ItemStatus.Unavailable
      }

      it("if the bib and item are both closed and the item has no location, then the item cannot be requested") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibDataWith(
          varFields = List(
            VarField(
              marcTag = Some("506"),
              subfields = List(
                MarcSubfield(tag = "f", content = "Closed.")
              )
            )
          )
        )

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "", display = ""),
            "88" -> FixedField(label = "STATUS", value = "h", display = "Closed"),
            "108" -> FixedField(label = "OPACMSG", value = "u", display = "Unavailable"),
          ),
          location = None
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = AccessStatus.Closed)
        )
        status shouldBe ItemStatus.Unavailable
      }

      it("and the item type does not permit requesting") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "61" -> FixedField(label = "I TYPE", value = "17", display = "film"),
            "79" -> FixedField(label = "LOCATION", value = "mfohc", display = "Closed stores Moving image and sound collections"),
            "88" -> FixedField(label = "STATUS", value = "-", display = "Available"),
            "108" -> FixedField(label = "OPACMSG", value = "a", display = "By appointment"),
          ),
          location = Some(SierraSourceLocation(code = "mfohc", name = "Closed stores Moving image and sound collections"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = AccessStatus.ByAppointment)
        )
        status shouldBe ItemStatus.Available
      }

      it("and the bib is restricted, but available for requesting") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibDataWith(
          varFields = List(
            VarField(
              marcTag = Some("506"),
              subfields = List(
                MarcSubfield(tag = "f", content = "Restricted.")
              )
            )
          )
        )

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "scmac", display = "Closed stores Arch. & MSS"),
            "88" -> FixedField(label = "STATUS", value = "6", display = "Restricted"),
            "108" -> FixedField(label = "OPACMSG", value = "f", display = "Online request"),
          ),
          location = Some(SierraSourceLocation(code = "scmac", name = "Closed stores Arch. & MSS"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = Some(AccessStatus.Restricted), terms = Some("Online request"))
        )
        status shouldBe ItemStatus.Available
      }

      it("if the item is for staff use only") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "61" -> FixedField(label = "I TYPE", value = "17", display = "film"),
            "79" -> FixedField(label = "LOCATION", value = "mfohc", display = "Closed stores Moving image and sound collections"),
            "88" -> FixedField(label = "STATUS", value = "-", display = "Available"),
            "108" -> FixedField(label = "OPACMSG", value = "s", display = "Staff use only"),
          ),
          location = Some(SierraSourceLocation(code = "mfohc", name = "Closed stores Moving image and sound collections"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = Some(AccessStatus.Unavailable), terms = Some("Staff use only"))
        )
        status shouldBe ItemStatus.Unavailable
      }

      it("if the item is on exhibition") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "61" -> FixedField(label = "I TYPE", value = "22", display = "exhibit"),
            "79" -> FixedField(label = "LOCATION", value = "exres", display = "On Exhibition"),
            "87" -> FixedField(label = "LOANRULE", value = "26"),
            "88" -> FixedField(label = "STATUS", value = "-", display = "Available"),
            "108" -> FixedField(label = "OPACMSG", value = "f", display = "Online request"),
          ),
          location = Some(SierraSourceLocation(code = "exres", name = "On Exhibition"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = Some(AccessStatus.TemporarilyUnavailable), terms = Some("Item is on Exhibition Reserve. Please ask at the Enquiry Desk"))
        )
        status shouldBe ItemStatus.TemporarilyUnavailable
      }

      it("if the bib and item are temporarily unavailable for internal assessment") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibDataWith(
          varFields = List(
            VarField(
              marcTag = Some("506"),
              subfields = List(
                MarcSubfield(tag = "f", content = "Temporarily Unavailable.")
              )
            )
          )
        )

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "scmac", display = "Closed stores Arch. & MSS"),
            "88" -> FixedField(label = "STATUS", value = "r", display = "Unavailable"),
            "108" -> FixedField(label = "OPACMSG", value = "u", display = "Unavailable"),
          ),
          location = Some(SierraSourceLocation(code = "scmac", name = "Closed stores Arch. & MSS")),
          varFields = List(
            VarField(
              fieldTag = Some("n"),
              content = Some("Temporarily unavailable: undergoing internal assessment")
            )
          )
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = Some(AccessStatus.TemporarilyUnavailable), note = Some("Temporarily unavailable: undergoing internal assessment"))
        )
        status shouldBe ItemStatus.TemporarilyUnavailable
      }

      it("if the item is temporarily unavailable for internal assessment") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "scmac", display = "Closed stores Visual"),
            "88" -> FixedField(label = "STATUS", value = "r", display = "Unavailable"),
            "108" -> FixedField(label = "OPACMSG", value = "u", display = "Unavailable"),
          ),
          location = Some(SierraSourceLocation(code = "hicon", name = "Closed stores Visual"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(status = AccessStatus.Unavailable)
        )
        status shouldBe ItemStatus.Unavailable
      }

      it("if you're meant to request the top item") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(0),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "bwith", display = "bound in above"),
            "88" -> FixedField(label = "STATUS", value = "b", display = "As above"),
            "108" -> FixedField(label = "OPACMSG", value = "-", display = "-"),
          ),
          location = Some(SierraSourceLocation(code = "bwith", name = "bound in above"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(terms = Some("Please request top item."))
        )
        status shouldBe ItemStatus.Unavailable
      }
    }

    describe("and has at least one hold") {
      it("if it's still in the stores, it can't be requested") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(1),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "sgeph", display = "Closed stores ephemera"),
            "88" -> FixedField(label = "STATUS", value = "-", display = "Available"),
            "108" -> FixedField(label = "OPACMSG", value = "f", display = "Online request"),
          ),
          location = Some(SierraSourceLocation(code = "sgeph", name = "Closed stores ephemera"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(
            status = Some(AccessStatus.TemporarilyUnavailable),
            terms = Some("Item is in use by another reader. Please ask at Enquiry Desk."))
        )
        status shouldBe ItemStatus.TemporarilyUnavailable
      }

      it("if it's on the holdshelf, it can't be requested") {
        val bibId = createSierraBibNumber
        val bibData = createSierraBibData

        val itemId = createSierraItemNumber
        val itemData = createSierraItemDataWith(
          holdCount = Some(1),
          fixedFields = Map(
            "79" -> FixedField(label = "LOCATION", value = "swms4", display = "Closed stores WMS 4"),
            "88" -> FixedField(label = "STATUS", value = "!", display = "On holdshelf"),
            "108" -> FixedField(label = "OPACMSG", value = "f", display = "Online request"),
          ),
          location = Some(SierraSourceLocation(code = "swms4", name = "Closed stores WMS 4"))
        )

        val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

        ac shouldBe List(
          AccessCondition(
            status = Some(AccessStatus.TemporarilyUnavailable),
            terms = Some("Item is in use by another reader. Please ask at Enquiry Desk."))
        )
        status shouldBe ItemStatus.TemporarilyUnavailable
      }
    }
  }
}
