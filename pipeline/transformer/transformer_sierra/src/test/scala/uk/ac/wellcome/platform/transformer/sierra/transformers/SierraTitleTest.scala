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
        MarcSubfield(
          tag = "b",
          content =
            "official organ of the American Association for Cancer Research, Inc.")
      ),
      "Cancer research : official organ of the American Association for Cancer Research, Inc."
    ),
    (
      List(
        MarcSubfield(tag = "a", content = "The “winter mind” :"),
        MarcSubfield(
          tag = "b",
          content = "William Bronk and American letters /"),
        MarcSubfield(tag = "c", content = "Burt Kimmelman.")
      ),
      "The “winter mind” : William Bronk and American letters / Burt Kimmelman."
    ),
  )

  it("constructs a title from 245 subfields a, b and c") {
    forAll(titleTestCases) {
      case (subfields, expectedTitle) =>
        val bibData = createSierraBibDataWith(
          varFields = List(
            createVarFieldWith(marcTag = "245", subfields = subfields)
          )
        )

        SierraTitle(bibData = bibData) shouldBe Some(expectedTitle)
    }
  }

  it("uses the first instance of MARC 245 if there are multiple instances") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "245",
          subfields = List(
            MarcSubfield(tag = "a", content = "A book with multiple covers")
          )
        ),
        createVarFieldWith(marcTag = "245")
      )
    )

    SierraTitle(bibData = bibData) shouldBe Some("A book with multiple covers")
  }

  it("joins the subfields if one of them is repeated") {
    // This is based on https://search.wellcomelibrary.org/iii/encore/record/C__Rb1057466?lang=eng&marcData=Y
    val bibData = createSierraBibDataWith(
      varFields = List(
        createVarFieldWith(
          marcTag = "245",
          subfields = List(
            MarcSubfield(tag = "a", content = "The Book of common prayer:"),
            MarcSubfield(
              tag = "b",
              content = "together with the Psalter or Psalms of David,"),
            MarcSubfield(
              tag = "b",
              content = "and the form and manner of making bishops")
          )
        )
      )
    )

    SierraTitle(bibData = bibData) shouldBe Some(
      "The Book of common prayer: together with the Psalter or Psalms of David, and the form and manner of making bishops"
    )
  }

  describe("throws a ShouldNotTransformException if it can't create a title") {
    it("if there is no MARC field 245") {
      val bibData = createSierraBibDataWith(
        varFields = List.empty
      )
      val caught = intercept[ShouldNotTransformException] {
        SierraTitle(bibData)
      }
      caught.getMessage should startWith("Could not find varField 245!")
    }

    it("if there are no subfields a, b or c") {
      val bibData = createSierraBibDataWith(
        varFields = List(
          createVarFieldWith(
            marcTag = "245",
            subfields = List.empty
          )
        )
      )
      val caught = intercept[ShouldNotTransformException] {
        SierraTitle(bibData)
      }
      caught.getMessage should startWith("No fields to construct title!")
    }
  }
}
