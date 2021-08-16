package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraDurationTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {

  it("should extract duration in milliseconds from 306") {
    val varFields = List(
      VarField(marcTag = "306", subfields = List(Subfield(tag = "a", content = "011012")))
    )

    getDuration(varFields) shouldBe Some(4212000)
  }

  it("should use first duration when multiple defined") {
    val varFields = List(
      VarField(marcTag = "306", subfields = List(Subfield(tag = "a", content = "001000"))),
      VarField(marcTag = "306", subfields = List(Subfield(tag = "a", content = "001132")))
    )

    getDuration(varFields) shouldBe Some(600000)
  }

  it("should not extract duration when varfield badly formatted") {
    val varFields = List(
      VarField(marcTag = "500", subfields = List(Subfield(tag = "a", content = "01xx1012")))
    )

    getDuration(varFields) shouldBe None
  }

  it("should not extract duration when incorrect varfield") {
    val varFields = List(
      VarField(marcTag = "500", subfields = List(Subfield(tag = "a", content = "011012")))
    )

    getDuration(varFields) shouldBe None
  }

  it("should not extract duration when incorrect subfield") {
    val varFields = List(
      VarField(marcTag = "306", subfields = List(Subfield(tag = "b", content = "011012")))
    )

    getDuration(varFields) shouldBe None
  }

  private def getDuration(varFields: List[VarField]): Option[Int] =
    SierraDuration(createSierraBibDataWith(varFields = varFields))
}
