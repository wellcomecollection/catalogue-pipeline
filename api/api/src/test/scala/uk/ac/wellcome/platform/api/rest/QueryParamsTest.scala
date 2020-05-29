package uk.ac.wellcome.platform.api.rest

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import io.circe.Json

class QueryParamsTest extends AnyFunSpec with Matchers {

  describe("includes / excludes") {

    object A

    object B

    val decoder = QueryParamsUtils.decodeIncludesAndExcludes(
      "a" -> A,
      "b" -> B,
    )

    def decode(str: String) =
      decoder.decodeJson(Json.fromString(str))

    it("should decode includes") {
      decode("a,b") shouldBe Right((List(A, B), Nil))
    }

    it("should decode excludes") {
      decode("!a,!b") shouldBe Right((Nil, List(A, B)))
    }

    it("should decode and mixture of includes and excludes") {
      decode("a,!b") shouldBe Right((List(A), List(B)))
    }

    it("should fail decoding when unrecognised values") {
      val result = decode("b,c,!d")
      result shouldBe a[Left[_, _]]
      result.left.get.message shouldBe
        "'c', 'd' are not valid values. Please choose one of: ['a', 'b']"
    }
  }
}
