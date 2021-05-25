package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import weco.catalogue.internal_model.locations.{AccessCondition, AccessStatus}
import weco.catalogue.source_model.generators.SierraDataGenerators
import weco.catalogue.source_model.sierra.Implicits._
import weco.catalogue.source_model.sierra.marc.{
  FixedField,
  MarcSubfield,
  VarField
}
import weco.catalogue.source_model.sierra.source.SierraSourceLocation
import weco.catalogue.source_model.sierra.{
  SierraBibData,
  SierraBibNumber,
  SierraItemData,
  SierraItemNumber,
  SierraTransformable
}

import java.io.{BufferedReader, File, FileInputStream, InputStreamReader}
//import java.util.zip.GZIPInputStream
import scala.util.{Failure, Success, Try}

class BufferedReaderIterator(reader: BufferedReader) extends Iterator[String] {
  override def hasNext = reader.ready
  override def next() = reader.readLine()
}

object GzFileIterator {
  def apply(file: java.io.File) = {
    new BufferedReaderIterator(
      new BufferedReader(
        new InputStreamReader(
//          new GZIPInputStream(
            new FileInputStream(file))))
  }
}


class SierraAccessConditionTest extends AnyFunSpec with Matchers with SierraDataGenerators {
  it("works") {
    val gz = GzFileIterator(new File("/Users/alexwlchan/desktop/sierra/out.json"))

    val bibItemPairs: Iterator[(SierraBibNumber, SierraBibData, SierraItemNumber, SierraItemData)] =
      gz
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

    bibItemPairs
      .filterNot {
        case (_, bibData, _, itemData) =>
          bibData.suppressed | bibData.deleted | itemData.suppressed | itemData.deleted
      }
      .filterNot { case (bibId, _, _, _) =>
        Set("1656190", "1656330",

          // Why is this not requestable?
          "1458108",

          // I TYPE = 15, shouldn't be requestable
          "1667902",

          // opacmsg = "manual request", surely shouldn't be requestable
          "1426986",

          // investigate further
          "1656560",
        ).contains(bibId.withoutCheckDigit)
      }
      .filterNot { case (_, _, _, itemData) =>
        itemData.location.map { _.code }.contains("bwith")
      }
      .filterNot {
        case (_, _, _, itemData) =>
          itemData.fixedFields.get("61").map { _.value }.getOrElse("") == "15"
      }
      .zipWithIndex.foreach { case ((bibId, bibData, itemId, itemData), idx) =>
        val ac = Try { SierraAccessCondition(bibId, bibData, itemId, itemData) }

        ac match {
          case Success(_) => ()
          case Failure(err) =>
            println(s"Got to $idx")
            println(bibId)
            println(bibData)
            println(itemId)
            println(itemData)
            throw err
        }
      }
  }

  it("an item on the open shelves is available and has no access conditions") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibData

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
      fixedFields = Map(
        "79" -> FixedField(label = "LOCATION", value = "wgmem", display = "Medical Collection"),
        "88" -> FixedField(label = "STATUS", value = "-", display = "Available"),
        "108" -> FixedField(label = "OPACMSG", value = "o", display = "Available"),
      ),
      location = Some(SierraSourceLocation(code = "wgmem", name = "Medical Collection"))
    )

    val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

    ac shouldBe empty
    status shouldBe ItemStatus.Available
  }

  it("an item in the closed stores is available and has no access conditions") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibData

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
      fixedFields = Map(
        "79" -> FixedField(label = "LOCATION", value = "scmac", display = "Closed stores Arch. & MSS"),
        "88" -> FixedField(label = "STATUS", value = "-", display = "Available"),
        "108" -> FixedField(label = "OPACMSG", value = "f", display = "Online request"),
      ),
      location = Some(SierraSourceLocation(code = "scmac", name = "Closed stores Arch. & MSS"))
    )

    val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

    ac shouldBe List(
      AccessCondition(
        status = Some(AccessStatus.Open),
        terms = Some("Online request")
      )
    )
    status shouldBe ItemStatus.Available
  }

  it("an item in the closed stores is available and has no access conditions (with bib status)") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("506"),
          subfields = List(MarcSubfield(tag = "f", content = "Open."))
        )
      )
    )

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
      fixedFields = Map(
        "79" -> FixedField(label = "LOCATION", value = "scmac", display = "Closed stores Arch. & MSS"),
        "88" -> FixedField(label = "STATUS", value = "-", display = "Available"),
        "108" -> FixedField(label = "OPACMSG", value = "f", display = "Online request"),
      ),
      location = Some(SierraSourceLocation(code = "scmac", name = "Closed stores Arch. & MSS"))
    )

    val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

    ac shouldBe List(
      AccessCondition(
        status = Some(AccessStatus.Open),
        terms = Some("Online request")
      )
    )
    status shouldBe ItemStatus.Available
  }

  it("an item that has 'as above' is not requestable (status = 'b')") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibData

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
      fixedFields = Map(
        "79" -> FixedField(label = "LOCATION", value = "bwith", display = "bound in above"),
        "88" -> FixedField(label = "STATUS", value = "b", display = "As above"),
        "108" -> FixedField(label = "OPACMSG", value = "-", display = "-"),
      ),
      location = Some(SierraSourceLocation(code = "bwith", name = "bound in above"))
    )

    val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

    ac shouldBe empty
    status shouldBe ItemStatus.Unavailable
  }

  it("an item that has 'as above' is not requestable (status = 'c')") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibData

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
      fixedFields = Map(
        "79" -> FixedField(label = "LOCATION", value = "bwith", display = "bound in above"),
        "88" -> FixedField(label = "STATUS", value = "c", display = "As above"),
        "108" -> FixedField(label = "OPACMSG", value = "-", display = "-"),
      ),
      location = Some(SierraSourceLocation(code = "cwith", name = "Contained in above"))
    )

    val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

    ac shouldBe empty
    status shouldBe ItemStatus.Unavailable
  }

  it("an item that is permission required (plus bib status)") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("506"),
          subfields = List(MarcSubfield(tag = "f", content = "By Appointment."))
        )
      )
    )

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
      fixedFields = Map(
        "79" -> FixedField(label = "LOCATION", value = "scmac", display = "Closed stores Arch. & MSS"),
        "88" -> FixedField(label = "STATUS", value = "y", display = "Permission required"),
        "108" -> FixedField(label = "OPACMSG", value = "a", display = "By appointment"),
      ),
      varFields = List(
        VarField(content = Some("Offsite"), fieldTag = Some("n"))
      ),
      location = Some(SierraSourceLocation(code = "scmac", name = "Closed stores Arch. & MSS"))
    )

    val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

    ac shouldBe List(AccessCondition(status = Some(AccessStatus.ByAppointment), note = Some("Offsite")))
    status shouldBe ItemStatus.Available
  }

  it("an item that is permission required (nothing on the bib)") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibData

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
      fixedFields = Map(
        "79" -> FixedField(label = "LOCATION", value = "hgser", display = "Offsite"),
        "88" -> FixedField(label = "STATUS", value = "y", display = "Permission required"),
        "108" -> FixedField(label = "OPACMSG", value = "a", display = "By appointment"),
      ),
      location = Some(SierraSourceLocation(code = "hgser", name = "Offsite"))
    )

    val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

    ac shouldBe List(AccessCondition(status = AccessStatus.ByAppointment))
    status shouldBe ItemStatus.Available
  }

  it("an item that is a manual request") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibData

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
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
        status = Some(AccessStatus.Open),
        terms = Some("Please complete a manual request slip.  This item cannot be requested online.")
      )
    )
    status shouldBe ItemStatus.Unavailable
  }

  it("an item that is missing") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibData

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
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

  it("an item that is withdrawn") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibData

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
      fixedFields = Map(
        "79" -> FixedField(label = "LOCATION", value = "ofjs1", display = "Closed stores Head, Research & Special Collections"),
        "88" -> FixedField(label = "STATUS", value = "x", display = "Withdrawn"),
        "108" -> FixedField(label = "OPACMSG", value = "-", display = "-"),
      ),
      location = Some(SierraSourceLocation(code = "ofjs1", name = "Closed stores Head, Research & Special Collections"))
    )

    val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

    ac shouldBe List(
      AccessCondition(
        status = Some(AccessStatus.Unavailable),
        terms = Some("This item is withdrawn.")
      )
    )
    status shouldBe ItemStatus.Unavailable
  }

  it("an item that is at digitisation") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibData

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
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

  it("an item that requires permission") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("506"),
          subfields = List(MarcSubfield(tag = "f", content = "Donor Permission."))
        )
      )
    )

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
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

  it("an item that is closed") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("506"),
          subfields = List(MarcSubfield(tag = "f", content = "Closed."))
        )
      )
    )

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
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

  it("an item that is restricted") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("506"),
          subfields = List(MarcSubfield(tag = "f", content = "Restricted."))
        )
      )
    )

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
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

  it("a film that is Staff Use only") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibData

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
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

  it("an item that is restricted due to data protection") {
    val bibId = createSierraBibNumber
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("506"),
          subfields = List(MarcSubfield(tag = "f", content = "Restricted."))
        )
      )
    )

    val itemId = createSierraItemNumber
    val itemData = createSierraItemDataWith(
      fixedFields = Map(
        "79" -> FixedField(label = "LOCATION", value = "sc#ac", display = "Unrequestable Arch. & MSS"),
        "88" -> FixedField(label = "STATUS", value = "6", display = "Restricted"),
        "108" -> FixedField(label = "OPACMSG", value = "f", display = "Online request"),
      ),
      location = Some(SierraSourceLocation(code = "sc#ac", name = "Unrequestable Arch. & MSS"))
    )

    val (ac, status) = SierraAccessCondition(bibId, bibData, itemId, itemData)

    ac shouldBe List(
      AccessCondition(status = Some(AccessStatus.Unavailable), terms = Some("Item not available due to provisions of Data Protection Act. Return to Archives catalogue to see when this file will be opened."))
    )
    status shouldBe ItemStatus.Unavailable
  }
}
