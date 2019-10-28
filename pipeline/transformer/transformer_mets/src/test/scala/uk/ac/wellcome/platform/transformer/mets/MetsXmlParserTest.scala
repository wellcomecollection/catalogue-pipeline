package uk.ac.wellcome.platform.transformer.mets

import scala.io.Source
import org.scalatest.{FunSpec, Matchers}

class MiroRecordTest extends FunSpec with Matchers {

  val xml =
    Source
      .fromInputStream(getClass.getResourceAsStream("/b30246039.xml"))
      .getLines
      .mkString

  it("parses recordIdentifier from XML") {
    MetsXmlParser(xml).recordIdentifier shouldBe Some("b30246039")
  }

  it("parses accessCondition from XML") {
    MetsXmlParser(xml).accessCondition shouldBe Some("CC-BY-NC")
  }
}
