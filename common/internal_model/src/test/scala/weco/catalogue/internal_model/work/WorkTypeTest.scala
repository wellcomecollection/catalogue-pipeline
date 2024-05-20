package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.Implicits._
import weco.json.JsonUtil.{fromJson, toJson}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class WorkTypeTest extends AnyFunSpec with Matchers {

  it("serialises WorkType to flat JSON") {
    workTypes.foreach {
      case (workType, expected) =>
        val json = toJson(workType).get
        json should be(quoted(expected))
    }
  }

  it("deserialises JSON as a WorkType") {
    workTypes.foreach {
      case (workType, string) =>
        val deserialised = fromJson[WorkType](quoted(string)).get
        deserialised shouldBe workType
    }
  }

  lazy val workTypes: Seq[(WorkType, String)] = Seq(
    (WorkType.Section, "Section"),
    (WorkType.Series, "Series"),
    (WorkType.Collection, "Collection"),
    (WorkType.Standard, "Standard")
  )

  def quoted(raw: String): String = s""""$raw""""

}
