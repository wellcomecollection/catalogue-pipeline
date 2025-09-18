package weco.lambda

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import io.circe.Json

class JavaMapJsonCodecTest extends AnyFunSpec with Matchers {
  import JavaMapJsonCodec._

  describe("JavaMapJsonCodec") {
    it("round-trips a flat object") {
      val json = Json.obj(
        "string" -> Json.fromString("value"),
        "int" -> Json.fromInt(5),
        "long" -> Json.fromLong(123456789L),
        "double" -> Json.fromDoubleOrNull(1.23),
        "bool" -> Json.fromBoolean(true)
      )
      val map = jsonToJavaMap(json)
      val back = javaMapToJson(map)
      back shouldBe json
    }

    it("round-trips nested objects and arrays") {
      val json = Json.obj(
        "outer" -> Json.obj(
          "inner" -> Json.arr(
            Json.fromInt(1),
            Json.fromInt(2),
            Json.fromString("three")
          )
        )
      )
      val map = jsonToJavaMap(json)
      val back = javaMapToJson(map)
      back shouldBe json
    }

    it("preserves big decimal scale beyond int/long range") {
      val big = Json.fromBigDecimal(BigDecimal("12345678901234567890.12345"))
      val json = Json.obj("big" -> big)
      val map = jsonToJavaMap(json)
      val back = javaMapToJson(map)
      back shouldBe json
    }

    it("handles null values") {
      val json = Json.obj(
        "maybe" -> Json.Null,
        "list" -> Json.arr(Json.Null, Json.fromString("x"))
      )
      val map = jsonToJavaMap(json)
      val back = javaMapToJson(map)
      back shouldBe json
    }

    it("converts non-object top-level jsonToJavaMap with error") {
      intercept[IllegalArgumentException] {
        jsonToJavaMap(Json.fromInt(1))
      }
    }
  }
}
