package weco.catalogue.source_model.sierra

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.sierra.models.identifiers.SierraBibNumber

import java.time.Instant

class SierraHoldingsRecordTest extends AnyFunSpec with Matchers {
  it("parses numeric bib IDs from a Sierra API response") {
    val jsonString =
      """{
        |    "id": 1064036,
        |    "bibIds": [
        |        1571482
        |    ],
        |    "itemIds": [
        |        1234567
        |    ],
        |    "updatedDate": "2003-04-02T13:29:42Z",
        |    "deleted": false,
        |    "suppressed": false
        |}""".stripMargin

    val record = SierraHoldingsRecord(
      id = "1064036",
      data = jsonString,
      modifiedDate = Instant.now
    )

    record.bibIds shouldBe List(SierraBibNumber("1571482"))
  }
}
