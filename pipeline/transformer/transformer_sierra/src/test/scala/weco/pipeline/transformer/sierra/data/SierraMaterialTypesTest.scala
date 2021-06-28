package weco.pipeline.transformer.sierra.data

import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Format.{Books, StudentDissertations}

class SierraMaterialTypesTest extends AnyFunSpec with Matchers with OptionValues {
  it("looks up a Format by code") {
    SierraMaterialTypes.fromCode("w").value shouldBe StudentDissertations
  }

  it("uses the linked format") {
    // v maps to E-books material type which is linked to Books
    SierraMaterialTypes.fromCode("v").value shouldBe Books
  }

  it("returns None if passed an unrecognised code") {
    SierraMaterialTypes.fromCode("?") shouldBe None
  }

  it("returns None if passed an empty string") {
    SierraMaterialTypes.fromCode("") shouldBe None
  }

  it("returns None if passed a code which is more than a single char") {
    SierraMaterialTypes.fromCode("XXX") shouldBe None
  }
}
