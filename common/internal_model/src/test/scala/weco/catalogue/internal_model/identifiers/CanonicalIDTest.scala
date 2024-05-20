package weco.catalogue.internal_model.identifiers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.{DynamoFormat, DynamoValue}
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import weco.catalogue.internal_model.Implicits._
import weco.json.JsonUtil.{fromJson, toJson}
import weco.json.utils.JsonAssertions

import scala.util.Success

class CanonicalIDTest extends AnyFunSpec with Matchers with JsonAssertions {
  it("checks it's really a canonical ID") {
    intercept[IllegalArgumentException] {
      CanonicalId("1")
    }

    intercept[IllegalArgumentException] {
      CanonicalId("AStringThatsMoreThanEightCharacters")
    }

    intercept[IllegalArgumentException] {
      CanonicalId("a string with space")
    }
  }

  describe("JSON encoding") {
    it("encodes as a string") {
      val id = CanonicalId("12345678")

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
      fromJson[CanonicalId](jsonString) shouldBe Success(
        CanonicalId("12345678")
      )
    }
  }

  describe("DynamoFormat") {
    it("encodes as a string") {
      val id = CanonicalId("12345678")
      DynamoFormat[CanonicalId].write(id) shouldBe DynamoValue.fromString(
        "12345678"
      )
    }

    it("decodes from a string") {
      val av = AttributeValue.builder().s("12345678").build()
      DynamoFormat[CanonicalId].read(av) shouldBe Right(CanonicalId("12345678"))
    }
  }
}
