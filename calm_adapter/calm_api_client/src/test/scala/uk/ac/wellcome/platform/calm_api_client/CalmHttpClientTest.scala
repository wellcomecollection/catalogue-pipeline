package uk.ac.wellcome.platform.calm_api_client

import akka.http.scaladsl.model._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.platform.calm_api_client.fixtures.CalmApiTestClient

class CalmHttpClientTest
    extends AnyFunSpec
    with Matchers
    with Akka
    with ScalaFutures
    with CalmApiTestClient {

  val protocol = HttpProtocols.`HTTP/1.0`

  val request = HttpRequest(uri = "http://calm.api")
  val response200 = HttpResponse(200, Nil, ":)", protocol)
  val response500 = HttpResponse(500, Nil, ":(", protocol)
  val response408 = HttpResponse(408, Nil, ":/", protocol)

  it("returns first API response when the status is OK") {
    val responses = List(response200, response500, response500, response500)
    withHttpClient(responses) { httpClient =>
      whenReady(httpClient(request)) { response =>
        response shouldBe response200
      }
    }
  }

  it("retries calling the API when status is not OK") {
    val responses = List(response500, response408, response200, response500)
    withHttpClient(responses) { httpClient =>
      whenReady(httpClient(request)) { response =>
        response shouldBe response200
      }
    }
  }

  it("throws an error if max retries exceeded") {
    val responses = List(response500, response500, response500, response500)
    withHttpClient(responses) { httpClient =>
      whenReady(httpClient(request).failed) { err =>
        err.getMessage shouldBe "Max retries attempted when calling Calm API"
      }
    }
  }
}
