package uk.ac.wellcome.platform.transformer.tei.transformer

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.tei.transformer.fixtures.TeiGenerators

class TeiXmlTest extends AnyFunSpec with Matchers with TeiGenerators {
  val id = "manuscript_15651"

  it("fails parsing a tei XML if the supplied id is different from the id in the XML"){
    val suppliedId = "another_id"
    val result = TeiXml(suppliedId, teiXml(id = id).toString())
    result shouldBe a [Left[_,_]]
    result.left.get.getMessage should include (suppliedId)
    result.left.get.getMessage should include (id)
  }

}
