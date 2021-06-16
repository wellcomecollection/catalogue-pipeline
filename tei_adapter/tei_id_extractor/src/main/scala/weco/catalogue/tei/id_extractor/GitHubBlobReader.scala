package weco.catalogue.tei.id_extractor

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import uk.ac.wellcome.json.JsonUtil._

import java.net.URI
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class GitHubBlobReader(implicit ac: ActorSystem) {
  implicit val ec = ac.dispatcher
  def getBlob(uri: URI): Future[String] = {
    val request = HttpRequest(uri = Uri(uri.toString), headers = List(
      Accept(MediaType.applicationWithFixedCharset("vnd.github.v3+json", HttpCharsets.`UTF-8`)),
      `Cache-Control`(CacheDirectives.`no-cache`), Connection("keep-alive")))
    for {
      response <- Http().singleRequest(request)
      blob <- unmarshalAs[Blob](response, request)
      decoded <- Future.fromTry(decodeBase64(blob.content))
    } yield decoded

  }
  private val base64 = java.util.Base64.getMimeDecoder

  private def decodeBase64(s: String) = Try {
  val bytes = base64.decode(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
}

  private def unmarshalAs[T](response: HttpResponse, request: HttpRequest)(implicit um: Unmarshaller[ResponseEntity, T]): Future[T] = {
    response match {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        Unmarshal(entity).to[T].recoverWith{
          case t: Throwable =>

            Future.failed(new RuntimeException(s"Failed to marshal Github api response for url ${request.uri.toString} with headers ${response.headers.toString()}: ${entity.toString}", t)
              )
        }
      case HttpResponse(code, _, entity, _) =>
        Unmarshal(entity).to[String].transform{
          case Success(responseEntity) => Failure(new RuntimeException(s"The GitHub api returned an error: ${code.value} for url ${request.uri.toString} with headers ${response.headers.toString()}: $responseEntity"))
          case _ => Failure(new RuntimeException(s"The GitHub api returned an error: ${code.value} for url ${request.uri.toString} with headers ${response.headers.toString()}: ${entity.toString}"))
        }
    }
  }
}

case class Blob(content: String, encoding: String)
