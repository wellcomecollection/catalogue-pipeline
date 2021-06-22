package uk.ac.wellcome.platform.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.calm.CalmRecord
import uk.ac.wellcome.json.JsonUtil._

import scala.collection.JavaConverters._
import java.io.{BufferedReader, FileReader}
import java.time.Instant
import scala.util.{Failure, Success, Try}

class CalmTermsOfUseTest extends AnyFunSpec with Matchers {
  it("covers all cases") {
    val source = new BufferedReader(new FileReader("/users/alexwlchan/desktop/items.json"))

    var handled = 0
    var unhandled = 0

    source.lines()
      .iterator().asScala
      .map { line => CalmRecord(
        id = "123",
        retrievedAt = Instant.now,
        data = fromJson[Map[String, List[String]]](line).get
      ) }
      .foreach { record =>
        Try { CalmTermsOfUse(record) } match {
          case Success(_) => handled += 1
          case Failure(err) =>
            println(record)
            println(err)
            println("")
            unhandled += 1
        }
      }

    println(s"handled = $handled, unhandled = $unhandled")
  }
}
