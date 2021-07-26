package weco.catalogue.tei.id_extractor.github

import akka.http.scaladsl.model.{
  HttpCharsets,
  HttpRequest,
  HttpResponse,
  MediaType
}
import akka.http.scaladsl.model.headers.{
  Accept,
  Authorization,
  OAuth2BearerToken
}
import weco.http.client.HttpClient

import scala.concurrent.Future

class GitHubAuthenticatedHttpClient(underlying: HttpClient, token: String) extends HttpClient {
  override def singleRequest(request: HttpRequest): Future[HttpResponse] =
    underlying.singleRequest(
      request.copy(
        headers = request.headers ++ List(
          // Send the version of GitHub API we expect as per https://docs.github.com/en/rest/overview/media-types
          Accept(
            MediaType.applicationWithFixedCharset(
              "vnd.github.v3+json",
              HttpCharsets.`UTF-8`)),
          Authorization(OAuth2BearerToken(token))
        )
      )
    )
}
