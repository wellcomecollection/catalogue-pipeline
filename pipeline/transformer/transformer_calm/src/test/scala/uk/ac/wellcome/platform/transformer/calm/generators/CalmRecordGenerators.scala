package uk.ac.wellcome.platform.transformer.calm.generators

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.platform.transformer.calm.CalmRecord

trait CalmRecordGenerators extends IdentifiersGenerators {

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
      id = createCalmRecordID,
      retrievedAt = randomInstant,
      data = data
    )
  }
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
      "Date" -> List("2020"))
  }
}
