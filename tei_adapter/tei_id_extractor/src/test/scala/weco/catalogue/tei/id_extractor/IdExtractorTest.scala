package weco.catalogue.tei.id_extractor

import org.apache.commons.io.IOUtils
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import scala.util.Success

class IdExtractorTest  extends AnyFunSpec with Matchers {
  it("extracts the id from a valid tei xml"){
    val idExtractor = new IdExtractor
    val triedId = idExtractor.extractId(IOUtils.resourceToString("/WMS_Arabic_1.xml", StandardCharsets.UTF_8))
    triedId shouldBe a[Success[_]]
    triedId.get shouldBe "manuscript_15651"
  }

  it("fails if xml is invalid"){
    fail()
  }
  it("fails if xml is valid but it's not tei"){
    fail()
  }
}
