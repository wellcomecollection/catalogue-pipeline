package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.fields.SierraMaterialType
import weco.sierra.models.marc.VarField

class SierraIconographicNumberTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators
    with TableDrivenPropertyChecks {

  private val matTypeTable = Table(
    ("code", "label"),
    ("k", "Pictures"),
    ("r", "3-D Objects")
  )

  forAll(matTypeTable) {
    case (code, label) =>
      it(s"uses the i-number from 001 if materialType = $label") {
        val bibData = createSierraBibDataWith(
          materialType = Some(SierraMaterialType(code)),
          varFields = List(
            VarField(
              marcTag = Some("001"),
              content = Some("12345i")
            )
          )
        )
        SierraIconographicNumber(bibData) shouldBe Some("12345i")
      }
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
  // TODO: Add no *valid* 001 - i.e. there is one, but they are not i numbers
  it("returns nothing if there is no 001") {
    val bibData = createSierraBibDataWith(
      materialType = Some(SierraMaterialType("r")),
      varFields = List()
    )

    SierraIconographicNumber(bibData) shouldBe None
  }
  describe("validating i-numbers") {
    val badINumbers = Table(
      ("iNumber", "label"),
      ("9", "be a single digit"),
      ("i", "be just the letter i'"),
      ("000x00000i", "contain non-numeric characters"),
      ("123456789i.1.0", "have more than one numeric suffix"),
      ("123456789i.a1", "contain non-numeric characters in the suffix")
    )
    forAll(badINumbers) {
      case (iNumber, label) =>
        it(s"An i-number can $label") {
          val bibData = createSierraBibDataWith(
            materialType = Some(SierraMaterialType("k")),
            varFields = List(
              VarField(
                marcTag = Some("001"),
                content = Some(iNumber)
              )
            )
          )
          SierraIconographicNumber(bibData) shouldBe None
        }
    }

    val iNumberVariations = Table(
      ("iNumber", "label"),
      ("9i", "be a single digit, followed by the letter i"),
      (
        "12345678900987654321i",
        "be any number of digits followed by the letter i'"
      ),
      ("123456789i.1", "have a numeric suffix, separated by a dot '.'"),
      ("123456789i.989898989891", "have a numeric suffix of any length")
    )

    forAll(iNumberVariations) {
      case (iNumber, label) =>
        it(s"An i-number can $label") {
          val bibData = createSierraBibDataWith(
            materialType = Some(SierraMaterialType("k")),
            varFields = List(
              VarField(
                marcTag = Some("001"),
                content = Some(iNumber)
              )
            )
          )
          SierraIconographicNumber(bibData) shouldBe Some(iNumber)
        }
    }
  }

  it(
    "ignores a value that doesn't look like an i-number when there are multiple 001s"
  ) {
    val bibData = createSierraBibDataWith(
      materialType = Some(SierraMaterialType("k")),
      varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("b1234567x")
        ),
        VarField(
          marcTag = Some("001"),
          content = Some("12345i")
        )
      )
    )
    SierraIconographicNumber(bibData) shouldBe Some("12345i")
  }

  it(
    "returns the first value that looks like an i-number when there are multiple 001s"
  ) {
    val bibData = createSierraBibDataWith(
      materialType = Some(SierraMaterialType("k")),
      varFields = List(
        VarField(
          marcTag = Some("001"),
          content = Some("12345i")
        ),
        VarField(
          marcTag = Some("001"),
          content = Some("54321i")
        )
      )
    )
    SierraIconographicNumber(bibData) shouldBe Some("12345i")
  }
}
