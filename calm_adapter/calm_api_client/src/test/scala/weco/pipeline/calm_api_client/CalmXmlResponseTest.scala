package weco.pipeline.calm_api_client

import java.time.Instant

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.apache.pekko.http.scaladsl.model.headers.{Cookie, HttpCookiePair}
import weco.catalogue.source_model.calm.CalmRecord

class CalmXmlResponseTest extends AnyFunSpec with Matchers {

  describe("Calm search response") {

    val cookie = Cookie(List(HttpCookiePair("key", "value")))

    it("parses the number of hits from a Calm search response") {
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
      CalmSearchResponse(xml, cookie).parse shouldBe Right(
        CalmSession(12, cookie)
      )
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
      CalmSearchResponse(xml, cookie).parse shouldBe a[Left[_, _]]
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

      CalmSearchResponse(xml, cookie).parse shouldBe a[Left[_, _]]
    }
  }

  describe("Calm summary response") {

    val retrievedAt = Instant.ofEpochSecond(123456)

    it("parses a record from a Calm summary response") {
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
      CalmSummaryResponse(xml, retrievedAt).parse shouldBe Right(
        CalmRecord(
          "123",
          Map(
            "RecordType" -> List("Component"),
            "IDENTITY" -> List(""),
            "RefNo" -> List("WT/B/2/5/2/3"),
            "Date" -> List("September 1996-April 2002  "),
            "Modified" -> List("30/01/2020"),
            "RecordID" -> List("123")
          ),
          retrievedAt
        )
      )
    }

    it("filters suppressed fields from a Calm summary response") {
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
                    <RefNo>WT/B/2/5/2/3</RefNo>
                    <RecordID>123</RecordID>
                    <Sensitive>dont-show</Sensitive>
                  </Summary>
                </SummaryList>
              </SummaryHeaderResult>
            </SummaryHeaderResponse>
          </soap:Body>
        </soap:Envelope>
      CalmSummaryResponse(
        xml,
        retrievedAt,
        Set("Sensitive")
      ).parse shouldBe Right(
        CalmRecord(
          "123",
          Map(
            "RefNo" -> List("WT/B/2/5/2/3"),
            "RecordID" -> List("123")
          ),
          retrievedAt
        )
      )
    }

    it("parses a Calm record with repeated fields") {
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
                    <RecordType>Something-Else</RecordType>
                    <RefNo>WT/B/2/5/2/3</RefNo>
                    <RecordID>123</RecordID>
                  </Summary>
                </SummaryList>
              </SummaryHeaderResult>
            </SummaryHeaderResponse>
          </soap:Body>
        </soap:Envelope>

      CalmSummaryResponse(xml, retrievedAt).parse shouldBe Right(
        CalmRecord(
          "123",
          Map(
            "RecordType" -> List("Component", "Something-Else"),
            "RefNo" -> List("WT/B/2/5/2/3"),
            "RecordID" -> List("123")
          ),
          retrievedAt
        )
      )
    }

    it("parses a Calm record when inner document has ISO-8859-1 encoding") {
      val xml =
        <soap:Envelope
            xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xmlns:xsd="http://www.w3.org/2001/XMLSchema">
          <soap:Body>
            <SummaryHeaderResponse xmlns="http://ds.co.uk/cs/webservices/">
              <SummaryHeaderResult>&lt;?xml version="1.0" encoding="ISO-8859-1"?&gt;&lt;SummaryList&gt;&lt;Summary&gt;&lt;RecordType&gt;Component&lt;/RecordType&gt;\n  &lt;RecordID&gt;6e4edfee-e702-4da6-be6d-c2e60ce79728&lt;/RecordID&gt;&lt;/Summary&gt;&lt;/SummaryList&gt;</SummaryHeaderResult>
            </SummaryHeaderResponse>
          </soap:Body>
        </soap:Envelope>
      CalmSummaryResponse(xml, retrievedAt).parse shouldBe Right(
        CalmRecord(
          "6e4edfee-e702-4da6-be6d-c2e60ce79728",
          Map(
            "RecordType" -> List("Component"),
            "RecordID" -> List("6e4edfee-e702-4da6-be6d-c2e60ce79728")
          ),
          retrievedAt
        )
      )
    }

    it("errors when no RecordID in document") {
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
                    <RefNo>WT/B/2/5/2/3</RefNo>
                  </Summary>
                </SummaryList>
              </SummaryHeaderResult>
            </SummaryHeaderResponse>
          </soap:Body>
        </soap:Envelope>
      CalmSummaryResponse(xml, retrievedAt).parse shouldBe a[Left[_, _]]
    }

    it("errors when multiple RecordIDs in document") {
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
                    <RefNo>WT/B/2/5/2/3</RefNo>
                    <RecordID>123</RecordID>
                    <RecordID>456</RecordID>
                  </Summary>
                </SummaryList>
              </SummaryHeaderResult>
            </SummaryHeaderResponse>
          </soap:Body>
        </soap:Envelope>
      CalmSummaryResponse(xml, retrievedAt).parse shouldBe a[Left[_, _]]
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
      CalmSummaryResponse(xml, retrievedAt).parse shouldBe a[Left[_, _]]
    }
  }
}
