package uk.ac.wellcome.calm_adapter

import scala.util.Try
import scala.xml.{Elem, XML, Node}

trait CalmXmlResponse {
  val root: Elem

  val responseTag: String

  /** CALM SOAP responses are of the form:
    *
    * <?xml version="1.0" encoding="utf-8"?>
    * <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" ...>
    *   <soap:Body>
    *     <responseTag>
    *       ... data ...
    *     </responseTag>
    *   </soap:Body>
    * </soap:Envelope>
    *
    * Here we return the data nested under the `responseTag` node
    */
  def responseNode: Either[Throwable, Node] =
    root
      .childWithTag("Envelope")
      .flatMap(_.childWithTag("Body"))
      .flatMap(_.childWithTag(responseTag))

  implicit class NodeOps(node: Node) {

    def childWithTag(tag: String): Either[Throwable, Node] =
      (node \ tag)
        .headOption
        .map(Right(_))
        .getOrElse(Left(new Exception(s"Could not find child with tag: $tag")))
  }
}

case class CalmSearchResponse(val root: Elem) extends CalmXmlResponse {

  val responseTag = "SearchResponse"

  /** The search response XML is of the form:
   *
   *  <SearchResponse xmlns="http://ds.co.uk/cs/webservices/">
   *    <SearchResult>n</SearchResult>
   *  </SearchResponse>
   *
   *  Here we extract an integer containing n (the number of hits)
   */
  def numHits: Either[Throwable, Int] =
    responseNode
      .flatMap(_.childWithTag("SearchResult"))
      .flatMap(result => Try(result.text.toInt).toEither)
}

object CalmSearchResponse {

  def apply(str: String): Either[Throwable, CalmSearchResponse] =
    Try(XML.loadString(str)).map(CalmSearchResponse(_)).toEither
}

case class CalmSummaryResponse(val root: Elem) extends CalmXmlResponse {

  val responseTag = "SummaryHeaderResponse"

  /** The summary response XML is of the form:
   *
   *  <SummaryHeaderResponse xmlns="http://ds.co.uk/cs/webservices/">
   *    <SummaryHeaderResult>
   *      <SummaryList>
   *        <Summary>
   *          <tag>value</tag>
   *          ...
   *        </Summary>
   *      </SummaryList>
   *    </SummaryHeaderResult>
   *  </SummaryHeaderResponse>
   *
   *  Here we extract a CalmRecord, which contains a mapping between each `tag`
   *  and `value`.
   */
  def record: Either[Throwable, CalmRecord] =
    responseNode
      .flatMap(_.childWithTag("SummaryHeaderResult"))
      .flatMap(_.childWithTag("SummaryList"))
      .flatMap(_.childWithTag("Summary"))
      .map { node =>
        CalmRecord(
          node
            .map(child => child.label -> child.text)
            .toMap
        )
      }
}

object CalmSummaryResponse {

  def apply(str: String): Either[Throwable, CalmSummaryResponse] =
    Try(XML.loadString(str)).map(CalmSummaryResponse(_)).toEither
}
