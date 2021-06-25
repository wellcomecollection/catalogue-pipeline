package weco.catalogue.tei.id_extractor

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal

import java.net.URI
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe.Decoder
import weco.json.JsonUtil._
import weco.http.client.HttpClient

class GitHubBlobContentReader(httpClient: HttpClient, token: String)(
  implicit ac: ActorSystem) {
  implicit val ec = ac.dispatcher
  def getBlob(uri: URI): Future[String] = {
    val request = HttpRequest(
      uri = Uri(uri.toString),
      headers = List(
        // Send the version of GitHub API we expect as per https://docs.github.com/en/rest/overview/media-types
        Accept(
          MediaType.applicationWithFixedCharset(
            "vnd.github.v3+json",
            HttpCharsets.`UTF-8`)),
        Authorization(OAuth2BearerToken(token))
      )
    )
    for {
      response <- httpClient.singleRequest(request)
      blob <- unmarshalAs[Blob](response, request)
      decoded <- Future.fromTry(decodeBase64(blob.content))
    } yield decoded

  }
  private val base64 = java.util.Base64.getMimeDecoder

  private def decodeBase64(s: String) = Try {
    val bytes =
      base64.decode(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
  }

  private def unmarshalAs[T](response: HttpResponse, request: HttpRequest)(
    implicit decoder: Decoder[T]): Future[T] = {
    response match {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        Unmarshal(entity).to[T].recoverWith {
          case t: Throwable =>
            Future.failed(new RuntimeException(
              s"Failed to unmarshal GitHub api response for url ${request.uri} with headers ${response.headers}: $entity",
              t))
        }
      case HttpResponse(code, _, entity, _) =>
        Unmarshal(entity).to[String].transform {
          case Success(responseEntity) =>
            Failure(new RuntimeException(
              s"The GitHub api returned an error: ${code.value} for url ${request.uri} with headers ${response.headers}: $responseEntity"))
          case _ =>
            Failure(new RuntimeException(
              s"The GitHub api returned an error: ${code.value} for url ${request.uri} with headers ${response.headers}: $entity (couldn't decode entity as string)"))
        }
    }
  }
}

// Represents a Blob as returned by the GitHub API https://docs.github.com/en/rest/reference/git#blobs
case class Blob(content: String, encoding: String)
