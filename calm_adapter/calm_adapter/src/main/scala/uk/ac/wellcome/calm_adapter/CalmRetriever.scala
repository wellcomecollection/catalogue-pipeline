package uk.ac.wellcome.calm_adapter

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal

trait CalmRetriever {

  def getRecords(query: CalmQuery): Future[List[CalmRecord]]
}

class HttpCalmRetriever(url: String, username: String, password: String)(
  implicit
  ec: ExecutionContext,
  materializer: ActorMaterializer,
  actorSystem: ActorSystem)
    extends CalmRetriever {

  def getRecords(query: CalmQuery): Future[List[CalmRecord]] =
    callApi(CalmSearchRequest(query), CalmSearchResponse(_))
      .flatMap {
        case (numHits, cookie) =>
          runSequentially(
            0 to numHits,
            (pos: Int) =>
              callApi(
                CalmSummaryRequest(pos),
                CalmSummaryResponse(_),
                Some(cookie))
                .map { case (record, _) => record }
          )
      }

  def callApi[T](xmlRequest: CalmXmlRequest,
                 toCalmXml: String => Either[Throwable, CalmXmlResponse[T]],
                 cookie: Option[Cookie] = None): Future[(T, Cookie)] =
    Http()
      .singleRequest(calmRequest(xmlRequest, cookie))
      .flatMap { resp =>
        parseBody(resp, toCalmXml)
          .map(value => (value, cookie.getOrElse(parseCookie(resp))))
      }

  def calmRequest(xmlRequest: CalmXmlRequest,
                  cookie: Option[Cookie]): HttpRequest = {
    val request =
      HttpRequest(uri = url)
        .withEntity(ContentTypes.`text/xml(UTF-8)`, xmlRequest.xml.toString)
        .addCredentials(BasicHttpCredentials(username, password))
        .addHeader(
          RawHeader("SOAPAction", "http://ds.co.uk/cs/webservices/Search")
        )
    cookie match {
      case Some(cookie) => request.addHeader(cookie)
      case None         => request
    }
  }

  def parseBody[T](
    resp: HttpResponse,
    toCalmXml: String => Either[Throwable, CalmXmlResponse[T]]): Future[T] =
    Unmarshal(resp.entity)
      .to[String]
      .flatMap { xmlStr =>
        Future.fromTry(toCalmXml(xmlStr).flatMap(_.parse).toTry)
      }

  def parseCookie(resp: HttpResponse): Cookie =
    resp.headers
      .collect {
        case `Set-Cookie`(cookie) => Cookie(cookie.pair)
      }
      .headOption
      .getOrElse(
        throw new Exception("Session cookie not found in CALM response"))

  def runSequentially[I, O](inputs: Seq[I],
                            f: I => Future[O]): Future[List[O]] =
    inputs.foldLeft(Future.successful[List[O]](Nil)) { (future, input) =>
      future.flatMap { results =>
        f(input).map(results :+ _)
      }
    }
}
