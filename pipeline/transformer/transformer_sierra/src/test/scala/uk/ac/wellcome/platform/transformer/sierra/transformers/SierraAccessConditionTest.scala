package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import weco.catalogue.source_model.generators.SierraDataGenerators
import weco.catalogue.source_model.sierra.Implicits._
import weco.catalogue.source_model.sierra._
import weco.catalogue.source_model.sierra.marc.FixedField
import weco.catalogue.source_model.sierra.source.SierraSourceLocation

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
        Set().contains(bibId.withoutCheckDigit)
      }
      .filterNot { case (_, _, _, itemData) =>
        itemData.location.map { _.code }.contains("bwith") | itemData.location.map { _.code }.contains("cwith")
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
    }
  }
}
