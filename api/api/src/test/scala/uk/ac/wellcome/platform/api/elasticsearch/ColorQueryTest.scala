package uk.ac.wellcome.platform.api.elasticsearch

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ColorQueryTest extends AnyFunSpec with Matchers {
  val colorQuery = new ColorQuery(
    binSizes = Seq(Seq(4, 6, 9), Seq(2, 4, 6), Seq(1, 3, 5)),
    binMinima = Seq(0f, 10f / 256, 10f / 256)
  )

  it("converts hex colors to hsv tuples") {
    val hexes = Seq("ff0000", "00ff00", "0000ff", "00fa9a")
    val tuples = hexes.map(ColorQuery.hexToHsv)

    tuples shouldBe Seq(
      (0f, 1f, 1f),
      (120f / 360f, 1f, 1f),
      (240f / 360f, 1f, 1f),
      (0.43600f, 1f, 0.98039216f),
    )
  }

  it("creates queries for signatures with given bin sizes") {
    val hexCode = "ffff00"
    val q = colorQuery("colorField", Seq(hexCode))

    q.fields should contain only "colorField"
    q.likeTexts shouldBe Seq(
      "7/0",
      "71/1",
      "269/2"
    )
  }

  it("creates queries for signatures with only some bins used") {
    val hexCode = "ffff00"
    val q = colorQuery("colorField", Seq(hexCode), binIndices = Seq(2))

    q.fields should contain only "colorField"
    q.likeTexts shouldBe Seq("269/2")
  }

  it("creates queries for multiple colors") {
    val hexCodes = Seq("ff0000", "00ff00", "0000ff")
    val q = colorQuery("colorField", hexCodes)

    q.fields should contain only "colorField"
    q.likeTexts shouldBe Seq(
      "7/0",
      "8/0",
      "9/0",
      "71/1",
      "72/1",
      "74/1",
      "268/2",
      "270/2",
      "273/2"
    )
  }
}
