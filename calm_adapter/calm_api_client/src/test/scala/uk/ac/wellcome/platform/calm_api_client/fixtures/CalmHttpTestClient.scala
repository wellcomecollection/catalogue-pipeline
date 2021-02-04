package uk.ac.wellcome.platform.calm_api_client.fixtures

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import uk.ac.wellcome.platform.calm_api_client.CalmHttpClient

import scala.concurrent.Future

class CalmHttpTestClient(var responses: List[HttpResponse])
    extends CalmHttpClient {
  var requests: List[HttpRequest] = Nil
  def apply(request: HttpRequest): Future[HttpResponse] = {
    val response = responses.headOption
    responses = responses.drop(1)
    requests = requests :+ request
    response
      .map(Future.successful)
      .getOrElse(Future.failed(new Exception("Request failed")))
  }
}
