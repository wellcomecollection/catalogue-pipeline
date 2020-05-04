package uk.ac.wellcome.sierra_adapter.model

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._

class SierraBibRecordTest
    extends FunSpec
    with Matchers
    with SierraGenerators {

  it("can cast a SierraBibRecord to JSON and back again") {
    val originalRecord = createSierraBibRecord

    val jsonString = toJson(originalRecord).get
    val parsedRecord = fromJson[SierraBibRecord](jsonString).get
    parsedRecord shouldEqual originalRecord
  }
}
