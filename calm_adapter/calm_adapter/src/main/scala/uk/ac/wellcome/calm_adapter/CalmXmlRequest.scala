package uk.ac.wellcome.calm_adapter

import scala.xml.Elem

trait CalmXmlRequest {

  def body: Elem

  def xml: Elem =
    <soap12:Envelope
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsd="http://www.w3.org/2001/XMLSchema"
        xmlns:soap12="http://www.w3.org/2003/05/soap-envelope">
      <soap12:Body>
        {body}
      </soap12:Body>
    </soap12:Envelope>
}

case class CalmSearchRequest(query: CalmQuery, dbName: String = "Catalog") {
  def body: Elem =
    <Search xmlns="http://ds.co.uk/cs/webservices/">
      <dbname>{dbName}</dbname>
      <elementSet>DC</elementSet>
      <expr>{query.queryString}</expr>
    </Search>
}

case class CalmSummaryRequest(pos: Int, dbName: String = "Catalog") {
  def body: Elem =
    <SummaryHeader xmlns="http://ds.co.uk/cs/webservices/">
      <dbname>{dbName}</dbname>
      <HitLstPos>{pos}</HitLstPos>
    </SummaryHeader>
}
