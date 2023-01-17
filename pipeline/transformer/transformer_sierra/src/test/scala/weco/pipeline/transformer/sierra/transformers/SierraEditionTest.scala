package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraEditionTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {

  val edition = "1st edition."

  val altEdition = "2nd edition."

  it("should extract edition when there is 250 data") {
    val varFields = List(
      VarField(
        marcTag = Some("250"),
        indicator2 = Some("1"),
        subfields = List(Subfield(tag = "a", content = edition))
      )
    )

    getEdition(varFields) shouldBe Some(edition)
  }

  it("should not extract edition when there no 250") {
    val varFields = List(
      VarField(
        marcTag = Some("251"),
        indicator2 = Some("1"),
        subfields = List(Subfield(tag = "a", content = edition))
      )
    )

    getEdition(varFields) shouldBe None
  }

  it("should not extract edition when there no 'a' subfield in 250") {
    val varFields = List(
      VarField(
        marcTag = Some("250"),
        indicator2 = Some("1"),
        subfields = List(Subfield(tag = "b", content = edition))
      )
    )

    getEdition(varFields) shouldBe None
  }

  it("should combine varfields contents when multiple 250s defined") {
    val varFields = List(
      VarField(
        marcTag = Some("250"),
        indicator2 = Some("1"),
        subfields = List(Subfield(tag = "a", content = edition))
      ),
      VarField(
        marcTag = Some("250"),
        indicator2 = Some("1"),
        subfields = List(Subfield(tag = "a", content = altEdition))
      )
    )

    getEdition(varFields) shouldBe Some("1st edition. 2nd edition.")
  }

  private def getEdition(varFields: List[VarField]): Option[String] =
    SierraEdition(createSierraBibDataWith(varFields = varFields))
}
