package weco.catalogue.internal_model.identifiers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.{DynamoFormat, DynamoValue}
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions

import scala.util.Success

class CanonicalIDTest extends AnyFunSpec with Matchers with JsonAssertions {
  it("checks it's really a canonical ID") {
    intercept[IllegalArgumentException] {
      CanonicalID("1")
    }

    intercept[IllegalArgumentException] {
      CanonicalID("AStringThatsMoreThanEightCharacters")
    }

    intercept[IllegalArgumentException] {
      CanonicalID("a string with space")
    }
  }

  describe("JSON encoding") {
    it("encodes as a string") {
      val id = CanonicalID("12345678")

      assertJsonStringsAreEqual(
        toJson(id).get,
        """
          |"12345678"
          |""".stripMargin
      )
    }

    it("decodes from a string") {
      val jsonString =
        """
          |"12345678"
          |""".stripMargin
      fromJson[CanonicalID](jsonString) shouldBe Success(
        CanonicalID("12345678"))
    }
  }

  describe("DynamoFormat") {
    it("encodes as a string") {
      val id = CanonicalID("12345678")
      DynamoFormat[CanonicalID].write(id) shouldBe DynamoValue.fromString(
        "12345678")
    }

    it("decodes from a string") {
      val av = AttributeValue.builder().s("12345678").build()
      DynamoFormat[CanonicalID].read(av) shouldBe Right(CanonicalID("12345678"))
    }
  }
}
