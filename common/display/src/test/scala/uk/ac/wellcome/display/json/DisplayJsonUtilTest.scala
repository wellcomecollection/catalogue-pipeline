package uk.ac.wellcome.display.json

import io.circe.generic.auto._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.utils.JsonAssertions

class DisplayJsonUtilTest extends AnyFunSpec with Matchers with JsonAssertions {
  case class Shape(
    name: String,
    colours: Option[List[String]],
    sides: Int
  )

  it("includes empty lists") {
    val square = Shape(name = "square", colours = Some(List()), sides = 4)
    assertJsonStringsAreEqual(
      DisplayJsonUtil.toJson(square),
      """{"name":"square", "colours": [], "sides": 4}"""
    )
  }

  it("omits null values") {
    val triangle = Shape(name = "triangle", colours = None, sides = 3)
    assertJsonStringsAreEqual(
      DisplayJsonUtil.toJson(triangle),
      """{"name":"triangle", "sides": 3}"""
    )
  }
}
