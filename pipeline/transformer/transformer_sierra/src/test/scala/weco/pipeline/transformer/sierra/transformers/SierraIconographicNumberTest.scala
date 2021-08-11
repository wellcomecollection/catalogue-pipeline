package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.fields.SierraMaterialType
import weco.sierra.models.marc.VarField

class SierraIconographicNumberTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {
  it("uses the i-number from 001 if materialType = Pictures") {
    val bibData = createSierraBibDataWith(
      materialType = Some(SierraMaterialType("k")),
      varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("12345i")
        )
      )
    )

    SierraIconographicNumber(bibData) shouldBe Some("12345i")
  }

  it("uses the i-number from 001 if materialType = 3-D Objects") {
    val bibData = createSierraBibDataWith(
      materialType = Some(SierraMaterialType("r")),
      varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("56789i")
        )
      )
    )

    SierraIconographicNumber(bibData) shouldBe Some("56789i")
  }

  it("ignores the contents of 001 for other material types") {
    val bibData = createSierraBibDataWith(
      materialType = Some(SierraMaterialType("a")),
      varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("56789i")
        )
      )
    )

    SierraIconographicNumber(bibData) shouldBe None
  }

  it("returns nothing if there is no 001") {
    val bibData = createSierraBibDataWith(
      materialType = Some(SierraMaterialType("r")),
      varFields = List()
    )

    SierraIconographicNumber(bibData) shouldBe None
  }

  it("ignores a value that doesn't look like an i-number") {
    def getIconographicNumber(s: String): Option[String] = {
      val bibData = createSierraBibDataWith(
        materialType = Some(SierraMaterialType("k")),
        varFields = List(
          VarField(
            marcTag = Some("001"),
            content = Some(s)
          )
        )
      )

      SierraIconographicNumber(bibData)
    }

    getIconographicNumber("12345i") shouldBe Some("12345i")
    getIconographicNumber("12345") shouldBe None
    getIconographicNumber("i") shouldBe None
  }
}
