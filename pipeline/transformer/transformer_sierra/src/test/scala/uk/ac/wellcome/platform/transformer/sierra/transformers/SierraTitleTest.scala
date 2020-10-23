package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import uk.ac.wellcome.platform.transformer.sierra.exceptions.ShouldNotTransformException
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import uk.ac.wellcome.platform.transformer.sierra.source.MarcSubfield

class SierraTitleTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  val titleTestCases = Table(
    ("subfields", "expectedTitle"),
    (
      List(MarcSubfield(tag = "a", content = "[Man smoking at window].")),
      "[Man smoking at window]."
    ),
    (
      List(
        MarcSubfield(tag = "a", content = "Cancer research :"),
        MarcSubfield(tag = "b", content = "official organ of the American Association for Cancer Research, Inc.")
      ),
      "Cancer research : official organ of the American Association for Cancer Research, Inc."
    ),
    (
      List(
        MarcSubfield(tag = "a", content = "The “winter mind” :"),
        MarcSubfield(tag = "b", content = "William Bronk and American letters /"),
        MarcSubfield(tag = "c", content = "Burt Kimmelman.")
      ),
      "The “winter mind” : William Bronk and American letters / Burt Kimmelman."
    ),
  )

  it("constructs a title from 245 subfields a, b and c") {
    forAll(titleTestCases) { case (subfields, expectedTitle) =>
      val bibData = createSierraBibDataWith(
        varFields = List(
          createVarFieldWith(marcTag = "245", subfields = subfields)
        )
      )

      val title = SierraTitle(bibId = createSierraBibNumber, bibData = bibData)
      title shouldBe Some(expectedTitle)
    }
  }

  it("passes through the title from the bib record") {
    val title = "Tickling a tiny turtle in Tenerife"
    assertTitleIsCorrect(
      bibDataTitle = title,
      expectedTitle = title
    )
  }

  it("throws a ShouldNotTransform exception if bibData has no title") {
    val bibData = createSierraBibDataWith(title = None)
    val caught = intercept[ShouldNotTransformException] {
      SierraTitle(createSierraBibNumber, bibData)
    }
    caught.getMessage shouldBe "Sierra record has no title!"
  }

  private def assertTitleIsCorrect(
    bibDataTitle: String,
    expectedTitle: String
  ) = {
    val bibData = createSierraBibDataWith(title = Some(bibDataTitle))
    SierraTitle(createSierraBibNumber, bibData) shouldBe Some(expectedTitle)
  }
}
