package uk.ac.wellcome.calm_adapter

import scala.xml.Elem
import scala.xml.Utility.trim
import java.time.LocalDate

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class CalmXmlRequestTest extends AnyFunSpec with Matchers {

  it("generates a CALM search request for a particular modified date") {
    val query = CalmQuery.ModifiedDate(LocalDate.of(2008, 10, 2))
    compareXML(
      CalmSearchRequest(query).xml,
      <soap12:Envelope
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:xsd="http://www.w3.org/2001/XMLSchema"
          xmlns:soap12="http://www.w3.org/2003/05/soap-envelope">
        <soap12:Body>
          <Search xmlns="http://ds.co.uk/cs/webservices/">
            <dbname>Catalog</dbname>
            <elementSet>DC</elementSet>
            <expr>Modified=02/10/2008</expr>
          </Search>
        </soap12:Body>
      </soap12:Envelope>
    )
  }

  it("generates a CALM sumamry request for a particular index") {
    compareXML(
      CalmSummaryRequest(123).xml,
      <soap12:Envelope
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:xsd="http://www.w3.org/2001/XMLSchema"
          xmlns:soap12="http://www.w3.org/2003/05/soap-envelope">
        <soap12:Body>
          <SummaryHeader xmlns="http://ds.co.uk/cs/webservices/">
            <dbname>Catalog</dbname>
            <HitLstPos>123</HitLstPos>
          </SummaryHeader>
        </soap12:Body>
      </soap12:Envelope>
    )
  }

  def compareXML(a: Elem, b: Elem) =
    trim(a).toString shouldBe trim(b).toString
}
