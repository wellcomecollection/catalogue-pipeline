package weco.catalogue.tei.id_extractor.fixtures

import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers

import scala.xml.Utility.trim
import scala.xml.XML

trait XmlAssertions extends Matchers {
  def assertXmlStringsAreEqual(x1: String, x2: String): Assertion =
    trim(XML.loadString(x1)) shouldBe trim(XML.loadString(x2))
}
