package weco.catalogue.internal_model.identifiers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.{DynamoFormat, DynamoValue}
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions

import scala.util.Success

class CanonicalIDTest extends AnyFunSpec with Matchers with JsonAssertions {
  describe("JSON encoding") {
    it("encodes as a string") {
      val id = CanonicalID("1234567")

      assertJsonStringsAreEqual(
        toJson(id).get,
        """
          |"1234567"
          |""".stripMargin
      )
    }

    it("decodes from a string") {
      val jsonString =
        """
          |"1234567"
          |""".stripMargin
      fromJson[CanonicalID](jsonString) shouldBe Success(CanonicalID("1234567"))
    }
  }

  describe("DynamoFormat") {
    it("encodes as a string") {
      val id = CanonicalID("1234567")
      DynamoFormat[CanonicalID].write(id) shouldBe DynamoValue.fromString("1234567")
    }

    it("decodes from a string") {
      val av = AttributeValue.builder().s("1234567").build()
      DynamoFormat[CanonicalID].read(av) shouldBe Right(CanonicalID("1234567"))
    }
  }
}
