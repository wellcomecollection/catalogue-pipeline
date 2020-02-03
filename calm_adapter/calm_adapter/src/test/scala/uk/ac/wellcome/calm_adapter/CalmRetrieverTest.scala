package uk.ac.wellcome.calm_adapter

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.{FunSpec, Matchers}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith

class CalmRetrieverTest extends FunSpec with Matchers with Akka {

  val url = "calm.api"
  val username = "calm-user"
  val password = "calm-password"

  it("generates a CALM search request for a particular modified date") {
  }

  def withCalmRetriever[R](responses: List[HttpResponse])(testWith: TestWith[ActorMaterializer, R]) = {
    withMaterializer { implicit materializer =>
      implicit val httpClient = createHttpClient(responses)
      new HttpCalmRetriever(url, username, password)
    }
  }

  def createHttpClient(responseList: List[HttpResponse]) =
    new CalmHttpClient {
      var responses = responseList
      var requests: List[HttpRequest] = Nil

      def apply(request: HttpRequest): Future[HttpResponse] = {
        val response = responses.headOption
        requests = requests :+ request
        responses = responses.tail
        response
          .map(Future.successful(_))
          .getOrElse(Future.failed(new Exception("Request failed")))
      }
    }
}
