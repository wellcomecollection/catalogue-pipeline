package weco.catalogue.source_model.generators

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.RandomGenerators
import weco.catalogue.source_model.calm.CalmRecord

trait CalmRecordGenerators extends RandomGenerators {

  def createCalmRecordId: String = randomUUID.toString

  def createCalmRecordWith(fields: (String, String)*): CalmRecord = {
    // Roll up the fields into Map[String, List[String]]
    // e.g.
    //
    //      fields = ("Place" -> "London", "Place" -> "Paris", "Date" -> "2020")
    //
    // becomes
    //
    //    data = Map("Place" -> List("London", "Paris"), "Date" -> List("2020"))
    //
    val data = fields.foldLeft(Map.empty[String, List[String]]) {
      case (existingData, (key, value)) =>
        val existingValue: List[String] = existingData.getOrElse(key, Nil)
        existingData + (key -> (existingValue :+ value))
    }

    CalmRecord(
      id = createCalmRecordId,
      retrievedAt = randomInstant,
      data = data
    )
  }

  def createCalmRecord: CalmRecord = createCalmRecordWith()
}

class CalmRecordGeneratorsTest
    extends AnyFunSpec
    with Matchers
    with CalmRecordGenerators {
  it("assembles the data correctly") {
    val record = createCalmRecordWith(
      "Place" -> "London",
      "Place" -> "Paris",
      "Date" -> "2020"
    )

    record.data shouldBe Map(
      "Place" -> List("London", "Paris"),
      "Date" -> List("2020")
    )
  }
}
