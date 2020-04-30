package uk.ac.wellcome.models.transformable.sierra

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.json.JsonUtil._

class SierraBibRecordTest extends AnyFunSpec with Matchers with SierraGenerators {

  it("can cast a SierraBibRecord to JSON and back again") {
    val originalRecord = createSierraBibRecord

    val jsonString = toJson(originalRecord).get
    val parsedRecord = fromJson[SierraBibRecord](jsonString).get
    parsedRecord shouldEqual originalRecord
  }
}
