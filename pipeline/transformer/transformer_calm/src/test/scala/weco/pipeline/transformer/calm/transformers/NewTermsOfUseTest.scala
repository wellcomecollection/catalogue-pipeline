package weco.pipeline.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.internal_model.work.TermsOfUse
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.generators.CalmRecordGenerators
import weco.json.JsonUtil._

import java.time.{Instant, LocalDate}
import scala.util.{Success, Try}
import scala.io.AnsiColor._

class NewTermsOfUseTest
    extends AnyFunSpec
    with Matchers
    with CalmRecordGenerators {
  it("works") {
    val t = scala.io.Source.fromFile(
      "//Users/alexwlchan/Desktop/tmp.ERstDSaz/unique_calm_list_items.json")

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
        case _ =>
          unhandled += 1
          println(s"$RED$record$RESET")
      }
    }

    println(s"*** total = ${handled + unhandled}, unhandled = $unhandled")
  }

  it("returns nothing if there's no data to make a note") {
    val termsOfUse = NewTermsOfUse(
      conditions = None,
      status = None,
      closedUntil = None,
      restrictedUntil = None)

    termsOfUse shouldBe None
  }

  it("returns the conditions if that's all we have") {
    val termsOfUse = NewTermsOfUse(
      conditions = Some(
        "RAMC/1218/1/2 is awaiting conservation treatment and is currently unfit for production."),
      status = None,
      closedUntil = None,
      restrictedUntil = None
    )

    termsOfUse shouldBe Some(TermsOfUse(
      "RAMC/1218/1/2 is awaiting conservation treatment and is currently unfit for production."))
  }

  it("returns the date if that's all we have") {
    val closedTermsOfUse = NewTermsOfUse(
      conditions = None,
      status = None,
      closedUntil = Some(LocalDate.of(2001, 1, 1)),
      restrictedUntil = None)
    closedTermsOfUse shouldBe Some(TermsOfUse("Closed until 1 January 2001."))

    val restrictedTermsOfUse = NewTermsOfUse(
      conditions = None,
      status = None,
      closedUntil = None,
      restrictedUntil = Some(LocalDate.of(2002, 2, 2)))
    restrictedTermsOfUse shouldBe Some(
      TermsOfUse("Restricted until 2 February 2002."))
  }

  it("appends the closed until date if it's not in the body of the condition") {
    val termsOfUse = NewTermsOfUse(
      conditions = Some("This item is closed and cannot be accessed."),
      status = None,
      closedUntil = Some(LocalDate.of(2071, 1, 1)),
      restrictedUntil = None
    )

    termsOfUse shouldBe Some(TermsOfUse(
      "This item is closed and cannot be accessed. Closed until 1 January 2071."))
  }

  it(
    "combines the conditions and the status if the status doesn't contain the conditions") {
    val termsOfUse = NewTermsOfUse(
      conditions = Some(
        "For preservation reasons this item cannot be produced to readers. Queries regarding access should be directed to the Archives and Manuscripts department"),
      status = Some(AccessStatus.ByAppointment),
      closedUntil = None,
      restrictedUntil = None
    )

    termsOfUse shouldBe Some(TermsOfUse(
      "For preservation reasons this item cannot be produced to readers. Queries regarding access should be directed to the Archives and Manuscripts department. By appointment."))
  }

  it("skips repeating the status if it's in the body of the conditions") {
    val termsOfUse = NewTermsOfUse(
      conditions = Some(
        "Access to some of the material in this sub-series is restricted or closed. See the description of each item for further information."),
      status = Some(AccessStatus.Restricted),
      closedUntil = None,
      restrictedUntil = None
    )

    termsOfUse shouldBe Some(TermsOfUse(
      "Access to some of the material in this sub-series is restricted or closed. See the description of each item for further information."))
  }

  describe("NewCalmTermsOfUse") {
    it("parses the date in a Calm record") {
      val record = createCalmRecordWith(
        ("ClosedUntil", "01/01/2097")
      )

      NewCalmTermsOfUse(record) shouldBe Some(
        TermsOfUse("Closed until 1 January 2097."))
    }
  }
}
