package weco.catalogue.sierra_adapter.json

import org.scalatest.TryValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._

import scala.util.Failure

class JsonOpsTest extends AnyFunSpec with Matchers with TryValues {
  import JsonOps._

  it("parses a String") {
    fromJson[StringOrInt](
      """
        |"1234"
        |""".stripMargin).success.value.underlying shouldBe "1234"
  }

  it("parses an Int") {
    fromJson[StringOrInt]("1234").success.value.underlying shouldBe "1234"
  }

  it("fails if the input isn't a string or int") {
    fromJson[StringOrInt]("true") shouldBe a[Failure[_]]
  }
}
