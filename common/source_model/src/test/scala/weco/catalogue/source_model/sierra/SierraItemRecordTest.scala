package weco.catalogue.source_model.sierra

import java.time.Instant
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.json.JsonUtil._
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.sierra.models.identifiers.SierraItemNumber

class SierraItemRecordTest
    extends AnyFunSpec
    with Matchers
    with SierraRecordGenerators {

  it("can cast a SierraItemRecord to JSON and back again") {
    val originalRecord = createSierraItemRecord

    val jsonString = toJson(originalRecord).get
    val parsedRecord = fromJson[SierraItemRecord](jsonString).get
    parsedRecord shouldEqual originalRecord
  }

  it("creates a SierraItemRecord from valid item JSON") {
    val itemId = createSierraRecordNumberString
    val bibId = createSierraBibNumber
    val data = s"""
       |{
       |  "id": "$itemId",
       |  "bibIds" : ["$bibId"]
       |}
       |""".stripMargin

    val result = SierraItemRecord(
      id = itemId,
      data = data,
      modifiedDate = Instant.now
    )

    result.id shouldBe SierraItemNumber(itemId)
    result.data shouldBe data
    result.bibIds shouldBe List(bibId)
  }

  it("throws an exception for invalid JSON") {
    assertCreatingFromDataFails(
      data = "not a json string",
      expectedMessage = "expected null got 'not a ...' (line 1, column 1)"
    )
  }

  it("throws an exception for valid JSON that doesn't contain bibIds") {
    assertCreatingFromDataFails(
      data = "{}",
      expectedMessage = "Missing required field: DownField(bibIds)"
    )
  }

  it("throws an exception when bibIds is not a list of strings") {
    assertCreatingFromDataFails(
      data = """{"bibIds":[1,2,3]}""",
      expectedMessage =
        "Got value '1' with wrong type, expecting string: DownArray,DownField(bibIds)"
    )
  }

  it("throws an exception when bibIds is not a list") {
    assertCreatingFromDataFails(
      data = """{"bibIds":"blah"}""",
      expectedMessage =
        "Got value '\"blah\"' with wrong type, expecting array: DownField(bibIds)"
    )
  }

  private def assertCreatingFromDataFails(
    data: String,
    expectedMessage: String
  ) {
    val caught = intercept[IllegalArgumentException] {
      SierraItemRecord(
        id = createSierraRecordNumberString,
        data = data,
        modifiedDate = Instant.now
      )
    }

    val message1 = caught.getMessage
    message1 shouldBe s"Error parsing bibIds from JSON <<$data>> (weco.json.exceptions.JsonDecodingError: $expectedMessage)"
  }
}
