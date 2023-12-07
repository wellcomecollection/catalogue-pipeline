package weco.pipeline.transformer.mets.transformer

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.LocalResources

class MetsXMLTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with LocalResources {
  describe("failure conditions") {
    it("fails if the input string is not an xml") {
      MetsXml("hagdf") shouldBe a[Left[_, _]]
    }

    it("fails if the input string is not METS xml") {
      MetsXml("<x>hello</x>") shouldBe a[Left[_, _]]
    }
  }
}
