package weco.pipeline.sierra_reader.parsers

import java.time.Instant
import io.circe.parser.parse
import org.scalatest.compatible.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.json.JsonUtil.toJson
import weco.json.utils.JsonAssertions
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.catalogue.source_model.sierra.{
  AbstractSierraRecord,
  SierraBibRecord,
  SierraHoldingsRecord,
  SierraItemRecord
}
import weco.sierra.models.identifiers.{SierraBibNumber, SierraHoldingsNumber}

class SierraRecordParserTest
    extends AnyFunSpec
    with Matchers
    with JsonAssertions
    with SierraRecordGenerators {
  it("parses a bib record") {
    val id = createSierraBibNumber
    val updatedDate = "2013-12-13T12:43:16Z"
    val jsonString =
      s"""
       |{
       |  "id": "$id",
       |  "updatedDate": "$updatedDate"
       |}
        """.stripMargin

    val expectedRecord = createSierraBibRecordWith(
      id = id,
      data = jsonString,
      modifiedDate = Instant.parse(updatedDate)
    )

    val json = parse(jsonString).right.get

    assertSierraRecordsAreEqual(
      SierraRecordParser(SierraBibRecord.apply)(json),
      expectedRecord
    )
  }

  it("parses an item record") {
    val id = createSierraItemNumber
    val updatedDate = "2014-04-14T14:14:14Z"

    // We need to encode the bib IDs as strings, _not_ as typed
    // objects -- the format needs to map what we get from the
    // Sierra API.
    //
    val bibIds = (1 to 4).map { _ =>
      createSierraRecordNumberString
    }
    val jsonString =
      s"""
       |{
       |  "id": "$id",
       |  "updatedDate": "$updatedDate",
       |  "bibIds": ${toJson(bibIds).get}
       |}
        """.stripMargin

    val expectedRecord = createSierraItemRecordWith(
      id = id,
      modifiedDate = Instant.parse(updatedDate),
      bibIds = bibIds.map(SierraBibNumber(_)).toList
    )

    val json = parse(jsonString).right.get

    assertSierraRecordsAreEqual(
      SierraRecordParser(SierraItemRecord.apply)(json),
      expectedRecord
    )
  }

  it("parses a holdings record") {
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

    val expectedRecord = createSierraHoldingsRecordWith(
      id = SierraHoldingsNumber("1064036"),
      modifiedDate = Instant.parse("2003-04-02T13:29:42Z"),
      bibIds = List(SierraBibNumber("1571482")),
      data = (_, _, _) => jsonString
    )

    val json = parse(jsonString).right.get

    assertSierraRecordsAreEqual(
      SierraRecordParser(SierraHoldingsRecord.apply)(json),
      expectedRecord
    )
  }

  it("parses the date from deleted records") {
    val id = createSierraBibNumber
    val deletedDate = "2014-01-31"
    val jsonString =
      s"""
         |{
         |  "id" : "$id",
         |  "deletedDate" : "$deletedDate",
         |  "deleted" : true
         |}
         |""".stripMargin

    val expectedRecord = createSierraBibRecordWith(
      id = id,
      data = jsonString,
      modifiedDate = Instant.parse(s"${deletedDate}T23:59:59.999999999Z")
    )

    val json = parse(jsonString).right.get

    assertSierraRecordsAreEqual(
      SierraRecordParser(SierraBibRecord.apply)(json),
      expectedRecord
    )
  }

  it("treats a deleted record as newer than an update on the same day") {
    // Regression test for https://github.com/wellcometrust/platform/issues/4173
    val id = createSierraBibNumber

    val updatedJsonString =
      s"""
       |{
       |  "id": "$id",
       |  "updatedDate": "2020-01-20T15:31:00Z"
       |}
        """.stripMargin

    val deletedJsonString =
      s"""
       |{
       |  "id" : "$id",
       |  "deletedDate" : "2020-01-20",
       |  "deleted" : true
       |}
       |""".stripMargin

    val updatedJson = parse(updatedJsonString).right.get
    val deletedJson = parse(deletedJsonString).right.get

    val updatedRecord = SierraRecordParser(SierraBibRecord.apply)(updatedJson)
    val deletedRecord = SierraRecordParser(SierraBibRecord.apply)(deletedJson)

    deletedRecord.modifiedDate.isAfter(updatedRecord.modifiedDate) shouldBe true
  }

  private def assertSierraRecordsAreEqual(
    x: AbstractSierraRecord[_],
    y: AbstractSierraRecord[_]): Assertion = {
    x.id shouldBe x.id
    assertJsonStringsAreEqual(x.data, y.data)
    x.modifiedDate shouldBe y.modifiedDate
  }
}
