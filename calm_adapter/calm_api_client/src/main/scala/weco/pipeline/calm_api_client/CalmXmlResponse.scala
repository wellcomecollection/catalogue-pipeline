package weco.pipeline.calm_api_client

import java.io.ByteArrayInputStream

import scala.util.Try
import scala.xml.{Elem, Node, NodeSeq, XML}
import java.time.Instant

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.headers.Cookie
import weco.catalogue.source_model.calm.CalmRecord

trait CalmXmlResponse[T] {
  val root: Elem

  val responseTag: String

  def parse: Either[Throwable, T]

  /** CALM SOAP responses are of the form:
    *
    * <?xml version="1.0" encoding="utf-8"?> <soap:Envelope
    * xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" ...> <soap:Body>
    * <responseTag> ... data ... </responseTag> </soap:Body> </soap:Envelope>
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

case class CalmSearchResponse(root: Elem, cookie: Cookie)
    extends CalmXmlResponse[CalmSession] {

  val responseTag = "SearchResponse"

  /** The search response XML is of the form:
    *
    * <SearchResponse xmlns="http://ds.co.uk/cs/webservices/">
    * <SearchResult>n</SearchResult> </SearchResponse>
    *
    * Here we extract an integer containing n (the number of hits)
    */
  def parse: Either[Throwable, CalmSession] =
    responseNode
      .flatMap(_.childWithTag("SearchResult"))
      .flatMap {
        result =>
          Try(result.text.toInt)
            .map(numHits => CalmSession(numHits, cookie))
            .toEither
      }
}

object CalmSearchResponse {

  def apply(
    str: String,
    cookie: Cookie
  ): Either[Throwable, CalmSearchResponse] =
    Try(XML.loadString(str)).map(CalmSearchResponse(_, cookie)).toEither

  def apply(
    bytes: Array[Byte],
    cookie: Cookie
  ): Either[Throwable, CalmSearchResponse] =
    Try(XML.load(new java.io.ByteArrayInputStream(bytes)))
      .map(CalmSearchResponse(_, cookie))
      .toEither
}

case class CalmSummaryResponse(
  root: Elem,
  retrievedAt: Instant,
  suppressedFields: Set[String] = Set.empty
) extends CalmXmlResponse[CalmRecord] {

  val responseTag = "SummaryHeaderResponse"

  /** The summary response XML is of the form:
    *
    * <SummaryHeaderResponse xmlns="http://ds.co.uk/cs/webservices/">
    * <SummaryHeaderResult> <SummaryList> <Summary> <tag>value</tag> ...
    * </Summary> </SummaryList> </SummaryHeaderResult> </SummaryHeaderResponse>
    *
    * Here we extract a CalmRecord, which contains a mapping between each `tag`
    * and `value`.
    */
  def parse: Either[Throwable, CalmRecord] =
    responseNode
      .flatMap(_.childWithTag("SummaryHeaderResult"))
      .flatMap {
        node =>
          // The response contains an inner XML document (ISO-8859-1 encoded)
          // within the top level UTF-8 one, so we need to parse this here if
          // it exists
          Try(XML.loadString(node.text)).toEither.left
            .flatMap(_ => node.childWithTag("SummaryList"))
      }
      .flatMap(_.childWithTag("Summary"))
      .map(_ \ "_")
      .flatMap {
        nodes =>
          val data = toMapping(nodes)
          data.get("RecordID") match {
            case Some(List(id)) =>
              Right(CalmRecord(id, data, retrievedAt))
            case Some(_) => Left(new Exception("Multiple RecordIDs found"))
            case None    => Left(new Exception("RecordID not found"))
          }
      }

  def toMapping(nodes: NodeSeq): Map[String, List[String]] =
    nodes
      .map(node => node.label -> node.text)
      .filterNot { case (name, _) => suppressedFields.contains(name) }
      .groupBy(_._1)
      .mapValues(_.map(_._2).toList)
}

object CalmSummaryResponse {

  def apply(
    str: String,
    retrievedAt: Instant,
    suppressedFields: Set[String]
  ): Either[Throwable, CalmSummaryResponse] =
    Try(XML.loadString(str))
      .map(CalmSummaryResponse(_, retrievedAt, suppressedFields))
      .toEither

  def apply(
    bytes: Array[Byte],
    retrievedAt: Instant,
    suppressedFields: Set[String]
  ): Either[Throwable, CalmSummaryResponse] =
    Try(XML.load(new java.io.ByteArrayInputStream(bytes)))
      .map(CalmSummaryResponse(_, retrievedAt, suppressedFields))
      .toEither
}

case class CalmAbandonResponse(root: Elem) extends CalmXmlResponse[Done] {
  val responseTag = "AbandonResponse"

  /** The session abandonment response XML is of the form:
    *
    * <AbandonResponse xmlns="http://ds.co.uk/cs/webservices/" />
    *
    * We just want to make sure it exists here.
    */
  def parse: Either[Throwable, Done] =
    responseNode.map(_ => Done)
}

object CalmAbandonResponse {

  def apply(bytes: Array[Byte]): Either[Throwable, CalmAbandonResponse] =
    Try(XML.load(new ByteArrayInputStream(bytes)))
      .map(CalmAbandonResponse.apply)
      .toEither

}
