package weco.catalogue.source_model.sierra

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import weco.catalogue.source_model.generators.SierraGenerators

import Implicits._

class SierraBibRecordTest
    extends AnyFunSpec
    with Matchers
    with SierraGenerators {

  it("can cast a SierraBibRecord to JSON and back again") {
    val originalRecord = createSierraBibRecord

    val jsonString = toJson(originalRecord).get
    val parsedRecord = fromJson[SierraBibRecord](jsonString).get
    parsedRecord shouldEqual originalRecord
  }
}
