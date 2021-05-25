package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import weco.catalogue.source_model.sierra.Implicits._
import weco.catalogue.source_model.sierra.{SierraBibData, SierraBibNumber, SierraItemData, SierraItemNumber, SierraTransformable}

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


class SierraAccessConditionTest extends AnyFunSpec with Matchers {
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

    bibItemPairs.foreach { case (bibId, bibData, itemId, itemData) =>
      val ac = Try { SierraAccessCondition(bibId, bibData, itemId, itemData) }

      ac match {
        case Success(_) => ()
        case Failure(err) =>
          println(bibId)
          println(bibData)
          println(itemId)
          println(itemData)
          throw err
      }
    }
  }
}
