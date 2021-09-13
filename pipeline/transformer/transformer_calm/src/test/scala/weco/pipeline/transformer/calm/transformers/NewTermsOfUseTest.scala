package weco.pipeline.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.generators.CalmRecordGenerators
import weco.json.JsonUtil._

import java.time.Instant
import scala.util.{Success, Try}
import scala.io.AnsiColor._

class NewTermsOfUseTest extends AnyFunSpec with Matchers with CalmRecordGenerators {
  it("works") {
    val t = scala.io.Source.fromFile("//Users/alexwlchan/Desktop/tmp.ERstDSaz/unique_calm_list_items.json")

    var handled = 0
    var unhandled = 0

    t.getLines().foreach { line =>
      val data = fromJson[Map[String, List[String]]](line).get
      val record = CalmRecord(
        id = createCalmRecordId,
        data = data,
        retrievedAt = Instant.now()
      )
      Try { NewCalmTermsOfUse(record) } match {
        case Success(value) =>
          handled += 1
          println(s"$GREEN${value}$RESET")
        case _              =>
          unhandled += 1
          println(s"$RED$record$RESET")
      }
    }

    println(s"*** total = ${handled + unhandled}, unhandled = $unhandled")
  }
  
  it("returns nothing if there's no data to make a note") {
    val termsOfUse = NewTermsOfUse(conditions = None, status = None, closedUntil = None, restrictedUntil = None)

    termsOfUse shouldBe None
  }
}
