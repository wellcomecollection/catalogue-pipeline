package uk.ac.wellcome.platform.transformer.sierra.data

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.Format.{Books, StudentDissertations}
import uk.ac.wellcome.platform.transformer.sierra.exceptions.SierraTransformerException

class SierraMaterialTypesTest extends AnyFunSpec with Matchers {
  it("looks up a Format by code") {
    SierraMaterialTypes.fromCode("w") shouldBe StudentDissertations
  }

  it("uses the linked format") {
    // v maps to E-books material type which is linked to Books
    SierraMaterialTypes.fromCode("v") shouldBe Books
  }

  it("throws an exception if passed an unrecognised code") {
    val caught = intercept[SierraTransformerException] {
      SierraMaterialTypes.fromCode("?")
    }
    caught.e.getMessage shouldBe "Unrecognised work type code: ?"
  }

  it("throws an exception if passed an empty string") {
    val caught = intercept[SierraTransformerException] {
      SierraMaterialTypes.fromCode("")
    }
    caught.e.getMessage shouldBe "Work type code is not a single character: <<>>"
  }

  it("throws an exception if passed a code which is more than a single char") {
    val caught = intercept[SierraTransformerException] {
      SierraMaterialTypes.fromCode("XXX")
    }
    caught.e.getMessage shouldBe "Work type code is not a single character: <<XXX>>"
  }
}
