package weco.catalogue.tei.id_extractor

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import uk.ac.wellcome.json.JsonUtil._

import java.net.URI
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class GitHubBlobReader(implicit ac: ActorSystem) {
  implicit val ec = ac.dispatcher
  def getBlob(uri: URI): Future[String] = {
    val request = HttpRequest(uri=Uri(uri.toString))
    for {
      response <- Http().singleRequest(request)
      blob <- unmarshalAs[Blob](response, uri)
      decoded <- Future.fromTry(decodeBase64(blob.content))
    } yield decoded

  }
  private val base64 = java.util.Base64.getMimeDecoder

  private def decodeBase64(s: String) = Try {
  val bytes = base64.decode(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
}

  private def unmarshalAs[T](response: HttpResponse, uri: URI)(implicit um: Unmarshaller[ResponseEntity, T]): Future[T] = {
    response match {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        Unmarshal(entity).to[T]
      case HttpResponse(code, _, entity, _) =>
        Unmarshal(entity).to[String].transform{
          case Success(responseEntity) => Failure(new RuntimeException(s"The GitHub api returned an error: ${code.value} for url ${uri.toString}: $responseEntity"))
          case _ => Failure(new RuntimeException(s"The GitHub api returned an error: ${code.value} for url ${uri.toString}"))
        }
    }
  }
}

case class Blob(content: String, encoding: String)
