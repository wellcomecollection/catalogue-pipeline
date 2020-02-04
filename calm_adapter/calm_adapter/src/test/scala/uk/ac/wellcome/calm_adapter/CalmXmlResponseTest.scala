package uk.ac.wellcome.calm_adapter

import org.scalatest.{FunSpec, Matchers}

class CalmXmlResponseTest extends FunSpec with Matchers {

  describe("CALM search response") {
    it("parses the number of hits from a CALM search response") {
      val xml =
        <soap:Envelope
            xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xmlns:xsd="http://www.w3.org/2001/XMLSchema">
          <soap:Body>
            <SearchResponse xmlns="http://ds.co.uk/cs/webservices/">
              <SearchResult>12</SearchResult>
            </SearchResponse>
          </soap:Body>
        </soap:Envelope>
      CalmSearchResponse(xml).parse shouldBe Right(12)
    }

    it("errors when num hits is not an integer") {
      val xml =
        <soap:Envelope
            xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xmlns:xsd="http://www.w3.org/2001/XMLSchema">
          <soap:Body>
            <SearchResponse xmlns="http://ds.co.uk/cs/webservices/">
              <SearchResult>twelve</SearchResult>
            </SearchResponse>
          </soap:Body>
        </soap:Envelope>
      CalmSearchResponse(xml).parse shouldBe a[Left[_, _]]
    }

    it("errors when invalid body") {
      val xml =
        <soap:Envelope
            xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xmlns:xsd="http://www.w3.org/2001/XMLSchema">
          <soap:Body>
            <UnexpectedResponse xmlns="http://ds.co.uk/cs/webservices/">
              <SearchResult>12</SearchResult>
            </UnexpectedResponse>
          </soap:Body>
        </soap:Envelope>
      CalmSearchResponse(xml).parse shouldBe a[Left[_, _]]
    }
  }

  describe("CALM suummary response") {
    it("parses a calm record CALM search response") {
      val xml =
        <soap:Envelope
            xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xmlns:xsd="http://www.w3.org/2001/XMLSchema">
          <soap:Body>
            <SummaryHeaderResponse xmlns="http://ds.co.uk/cs/webservices/">
              <SummaryHeaderResult>
                <SummaryList>
                  <Summary>
                    <RecordType>Component</RecordType>
                    <IDENTITY></IDENTITY>
                    <RefNo>WT/B/2/5/2/3</RefNo>
                    <Date>September 1996-April 2002  </Date>
                    <Modified><span class="HIT">30</span>/01/2020</Modified>
                    <RecordID>123</RecordID>
                  </Summary>
                </SummaryList>
              </SummaryHeaderResult>
            </SummaryHeaderResponse>
          </soap:Body>
        </soap:Envelope>
      CalmSummaryResponse(xml).parse shouldBe Right(
        CalmRecord(
          "123",
          Map(
            "RecordType" -> "Component",
            "IDENTITY" -> "",
            "RefNo" -> "WT/B/2/5/2/3",
            "Date" -> "September 1996-April 2002  ",
            "Modified" -> "30/01/2020",
            "RecordID" -> "123"
          )
        )
      )
    }

    it("errors when invalid summary data") {
      val xml =
        <soap:Envelope
            xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xmlns:xsd="http://www.w3.org/2001/XMLSchema">
          <soap:Body>
            <SummaryHeaderResponse xmlns="http://ds.co.uk/cs/webservices/">
              <SummaryHeaderResult>
                <SummaryList>oops</SummaryList>
              </SummaryHeaderResult>
            </SummaryHeaderResponse>
          </soap:Body>
        </soap:Envelope>
      CalmSummaryResponse(xml).parse shouldBe a[Left[_, _]]
    }
  }
}
