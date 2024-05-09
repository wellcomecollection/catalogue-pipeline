package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraDurationTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {

  val hours = 60 * 60
  val minutes = 60
  val seconds = 1

  it("extracts duration in seconds from 306") {
    val varFields = List(
      VarField(
        marcTag = "306",
        subfields = List(Subfield(tag = "a", content = "011012"))
      )
    )

    getDuration(varFields) shouldBe Some(
      1 * hours + 10 * minutes + 12 * seconds
    )
  }

  it("uses the first duration when multiple defined") {
    val varFields = List(
      VarField(
        marcTag = "306",
        subfields = List(Subfield(tag = "a", content = "001000"))
      ),
      VarField(
        marcTag = "306",
        subfields = List(Subfield(tag = "a", content = "001132"))
      )
    )

    getDuration(varFields) shouldBe Some(10 * minutes)
  }

  it("does not extract duration when varfield badly formatted") {
    val varFields = List(
      VarField(
        marcTag = "306",
        subfields = List(Subfield(tag = "a", content = "01xx1012"))
      )
    )

    getDuration(varFields) shouldBe None
  }

  it("does not extract duration when incorrect varfield") {
    val varFields = List(
      VarField(
        marcTag = "500",
        subfields = List(Subfield(tag = "a", content = "011012"))
      )
    )

    getDuration(varFields) shouldBe None
  }

  it("does not extract duration when incorrect subfield") {
    val varFields = List(
      VarField(
        marcTag = "306",
        subfields = List(Subfield(tag = "b", content = "011012"))
      )
    )

    getDuration(varFields) shouldBe None
  }

  private def getDuration(varFields: List[VarField]): Option[Int] =
    SierraDuration(createSierraBibDataWith(varFields = varFields))
}
