package uk.ac.wellcome.platform.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.calm.CalmRecord
import uk.ac.wellcome.json.JsonUtil._
import weco.catalogue.internal_model.work.TermsOfUse
import weco.catalogue.source_model.generators.CalmRecordGenerators

import scala.collection.JavaConverters._
import java.io.{BufferedReader, FileReader}
import java.time.Instant
import scala.util.{Failure, Success, Try}

class CalmTermsOfUseTest extends AnyFunSpec with Matchers with CalmRecordGenerators {
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

  it("handles an item which is open") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Open"),
      ("AccessConditions", "The papers are available subject to the usual conditions of access to Archives and Manuscripts material.")
    )

    CalmTermsOfUse(record) shouldBe Some(TermsOfUse("The papers are available subject to the usual conditions of access to Archives and Manuscripts material. Open."))
  }

  it("handles an item which is closed") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Closed"),
      ("AccessConditions", "Closed on depositor agreement."),
    )

    CalmTermsOfUse(record) shouldBe Some(TermsOfUse("Closed on depositor agreement. Closed."))
  }

  it("handles an item which is restricted") {
    val record = createCalmRecordWith(
      ("AccessStatus", "Restricted"),
      ("AccessConditions", "Digital records cannot be ordered or viewed online. Requests to view digital records onsite are considered on a case by case basis. Please contact collections@wellcome.ac.uk for more details."),
    )

    CalmTermsOfUse(record) shouldBe Some(TermsOfUse("Digital records cannot be ordered or viewed online. Requests to view digital records onsite are considered on a case by case basis. Please contact collections@wellcome.ac.uk for more details. Restricted."))
  }
}
