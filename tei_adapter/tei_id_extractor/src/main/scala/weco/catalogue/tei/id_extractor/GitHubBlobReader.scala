package weco.catalogue.tei.id_extractor

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import uk.ac.wellcome.json.JsonUtil._

import scala.concurrent.Future
import scala.util.Try

class GitHubBlobReader(implicit ac: ActorSystem) {
  implicit val ec = ac.dispatcher
  def getBlob(uri: Uri): Future[Either[Throwable, String]] = {
    val request = HttpRequest(uri=uri)
    val f = for {
      response <- Http().singleRequest(request)
      blob <- unmarshalAs[Blob](response)
      decoded <- Future.fromTry(decodeBase64(blob.content))
    } yield Right(decoded)
    f.recover{
      case t: Throwable => Left(t)
    }
  }
  private val base64 = java.util.Base64.getMimeDecoder

  private def decodeBase64(s: String) = Try {
  val bytes = base64.decode(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
}

  private def unmarshalAs[T](response: HttpResponse)(implicit um: Unmarshaller[ResponseEntity, T]): Future[T] = {
    response match {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        Unmarshal(entity).to[T]
      case _ => ???
    }
  }
}

case class Blob(content: String, encoding: String)
