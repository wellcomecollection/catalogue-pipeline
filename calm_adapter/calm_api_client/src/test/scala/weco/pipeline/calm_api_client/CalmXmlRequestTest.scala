package weco.pipeline.calm_api_client

import java.time.LocalDate

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.xml.Elem
import scala.xml.Utility.trim

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
            <expr>(Modified=02/10/2008)</expr>
          </Search>
        </soap12:Body>
      </soap12:Envelope>
    )
  }

  it(
    "generates a CALM search request for a particular created or modified date"
  ) {
    val query = CalmQuery.CreatedOrModifiedDate(LocalDate.of(2008, 10, 2))
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
            <expr>(Created=02/10/2008)OR(Modified=02/10/2008)</expr>
          </Search>
        </soap12:Body>
      </soap12:Envelope>
    )
  }

  it("generates a CALM search request for records without created / modified") {
    val query = CalmQuery.EmptyCreatedAndModifiedDate
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
            <expr>(Created!=*)AND(Modified!=*)</expr>
          </Search>
        </soap12:Body>
      </soap12:Envelope>
    )
  }

  it("generates a CALM search request for a particular ref no") {
    val query = CalmQuery.RefNo("ArchiveNum*")
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
            <expr>(RefNo=ArchiveNum*)</expr>
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
