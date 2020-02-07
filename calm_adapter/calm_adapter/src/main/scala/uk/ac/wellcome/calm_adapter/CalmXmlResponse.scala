package uk.ac.wellcome.calm_adapter

import scala.util.Try
import scala.xml.{Elem, Node, XML}
import java.time.Instant
import akka.http.scaladsl.model.headers.Cookie

trait CalmXmlResponse[T] {
  val root: Elem

  val responseTag: String

  def parse: Either[Throwable, T]

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
      .childWithTag("Body")
      .flatMap(_.childWithTag(responseTag))

  implicit class NodeOps(node: Node) {

    def childWithTag(tag: String): Either[Throwable, Node] =
      (node \ tag).headOption
        .map(Right(_))
        .getOrElse(Left(new Exception(s"Could not find child with tag: $tag")))
  }
}

case class CalmSearchResponse(val root: Elem, cookie: Cookie)
    extends CalmXmlResponse[CalmSession] {

  val responseTag = "SearchResponse"

  /** The search response XML is of the form:
    *
    *  <SearchResponse xmlns="http://ds.co.uk/cs/webservices/">
    *    <SearchResult>n</SearchResult>
    *  </SearchResponse>
    *
    *  Here we extract an integer containing n (the number of hits)
    */
  def parse: Either[Throwable, CalmSession] =
    responseNode
      .flatMap(_.childWithTag("SearchResult"))
      .flatMap { result =>
        Try(result.text.toInt)
          .map(numHits => CalmSession(numHits, cookie))
          .toEither
      }
}

object CalmSearchResponse {

  def apply(str: String,
            cookie: Cookie): Either[Throwable, CalmSearchResponse] =
    Try(XML.loadString(str)).map(CalmSearchResponse(_, cookie)).toEither
}

case class CalmSummaryResponse(val root: Elem, retrievedAt: Instant)
    extends CalmXmlResponse[CalmRecord] {

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
  def parse: Either[Throwable, CalmRecord] =
    responseNode
      .flatMap(_.childWithTag("SummaryHeaderResult"))
      .flatMap(_.childWithTag("SummaryList"))
      .flatMap(_.childWithTag("Summary"))
      .map(_ \ "_")
      .flatMap { node =>
        val data = node.map(child => child.label -> child.text).toMap
        data
          .get("RecordID")
          .map(id => Right(CalmRecord(id, data, retrievedAt)))
          .getOrElse(Left(new Exception("RecordID not found")))
      }
}

object CalmSummaryResponse {

  def apply(str: String,
            retrievedAt: Instant): Either[Throwable, CalmSummaryResponse] =
    Try(XML.loadString(str)).map(CalmSummaryResponse(_, retrievedAt)).toEither
}
