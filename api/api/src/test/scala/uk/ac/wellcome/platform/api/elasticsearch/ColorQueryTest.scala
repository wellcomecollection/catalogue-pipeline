package uk.ac.wellcome.platform.api.elasticsearch

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ColorQueryTest extends AnyFunSpec with Matchers {
  val colorQuery = new ColorQuery(binSizes = Seq(4, 6, 8))

  it("converts hex colors to rgb tuples") {
    val hexes = Seq("ff0000", "00ff00", "0000ff", "00fa9a")
    val tuples = hexes.map(ColorQuery.hexToRgb)

    tuples shouldBe Seq(
      (255, 0, 0),
      (0, 255, 0),
      (0, 0, 255),
      (0, 250, 154),
    )
  }

  it("creates queries for signatures with given bin sizes") {
    val hexCode = "ffff00"
    val q = colorQuery("colorField", Seq(hexCode))

    q.fields should contain only "colorField"
    q.likeTexts shouldBe Seq(
      "15/4",
      "35/6",
      "63/8"
    )
  }

  it("creates queries for multiple colors") {
    val hexCodes = Seq("ff0000", "00ff00", "0000ff")
    val q = colorQuery("colorField", hexCodes)

    q.fields should contain only "colorField"
    q.likeTexts shouldBe Seq(
      "3/4",
      "12/4",
      "48/4",
      "5/6",
      "30/6",
      "180/6",
      "7/8",
      "56/8",
      "448/8"
    )
  }
}
