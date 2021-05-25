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
import java.util.zip.GZIPInputStream
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
          new GZIPInputStream(
            new FileInputStream(file)))))
  }
}


class SierraAccessConditionTest extends AnyFunSpec with Matchers with SierraDataGenerators {
  it("works") {
    val gz = GzFileIterator(new File("/Users/alexwlchan/desktop/sierra/out.json.gz"))

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

  it("an item that has 'as above' is not requestable") {
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

  it("an item that is permission required") {
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
      location = Some(SierraSourceLocation(code = "scmac", name = "Closed stores Arch. & MSS"))
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
}
