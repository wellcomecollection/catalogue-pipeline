package weco.catalogue.source_model.sierra

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.sierra.models.identifiers.{SierraBibNumber, SierraOrderNumber}

import java.time.Instant

class SierraOrderRecordTest extends AnyFunSpec with Matchers {
  it("gets the bib IDs from Sierra JSON") {
    val jsonString =
      """
        |{
        |  "accountingUnit": 0,
        |  "bibs": [
        |    "https://libsys.wellcomelibrary.org/iii/sierra-api/v6/bibs/2000002",
        |    "https://libsys.wellcomelibrary.org/iii/sierra-api/v6/bibs/3000003"
        |  ],
        |  "chargedFunds": [],
        |  "createdDate": "2000-03-07T14:07:14Z",
        |  "estimatedPrice": 18.95,
        |  "fixedFields": {},
        |  "id": 1000002,
        |  "orderDate": "2000-03-07T00:00:00Z",
        |  "suppressed": false,
        |  "updatedDate": "2010-03-16T19:23:48Z",
        |  "varFields": [],
        |  "vendorRecordCode": "c"
        |}
        |""".stripMargin

    val record = SierraOrderRecord(
      id = "1000002",
      data = jsonString,
      modifiedDate = Instant.parse("2010-03-16T19:23:48Z")
    )

    record shouldBe SierraOrderRecord(
      id = SierraOrderNumber("1000002"),
      data = jsonString,
      modifiedDate = Instant.parse("2010-03-16T19:23:48Z"),
      bibIds = List(SierraBibNumber("2000002"), SierraBibNumber("3000003"))
    )
  }
}
