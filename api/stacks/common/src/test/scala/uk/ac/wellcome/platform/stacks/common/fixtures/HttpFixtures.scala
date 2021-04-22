package uk.ac.wellcome.platform.stacks.common.fixtures

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.{GET, POST}
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpHeader,
  HttpRequest,
  HttpResponse,
  RequestEntity
}
import akka.stream.scaladsl.Sink
import io.circe.parser._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.stacks.common.http.config.models.HTTPServerConfig

trait HttpFixtures extends Akka with ScalaFutures with Matchers {

  private def whenRequestReady[R](
    r: HttpRequest
  )(testWith: TestWith[HttpResponse, R]): R =
    withActorSystem { implicit actorSystem =>
      val request = Http().singleRequest(r)
      whenReady(request) { response: HttpResponse =>
        testWith(response)
      }
    }

  def whenGetRequestReady[R](
    path: String,
    headers: List[HttpHeader] = Nil
  )(testWith: TestWith[HttpResponse, R]): R = {

    val request = HttpRequest(
      method = GET,
      uri = s"${externalBaseURL}${path}",
      headers = headers
    )

    whenRequestReady(request) { response =>
      testWith(response)
    }
  }

  def createJsonHttpEntityWith(jsonString: String): RequestEntity =
    HttpEntity(
      ContentTypes.`application/json`,
      parse(jsonString).right.get.noSpaces
    )

  def whenPostRequestReady[R](
    path: String,
    entity: RequestEntity,
    headers: List[HttpHeader] = Nil
  )(
    testWith: TestWith[HttpResponse, R]
  ): R = {

    val request = HttpRequest(
      method = POST,
      uri = s"${externalBaseURL}${path}",
      headers = headers,
      entity = entity
    )

    whenRequestReady(request) { response =>
      testWith(response)
    }
  }

  def withStringEntity[R](
    httpEntity: HttpEntity
  )(testWith: TestWith[String, R]): R =
    withMaterializer { implicit mat =>
      val value =
        httpEntity.dataBytes.runWith(Sink.fold("") {
          case (acc, byteString) =>
            acc + byteString.utf8String
        })
      whenReady(value) { string =>
        testWith(string)
      }
    }

  private val port = 1234
  private val host = "localhost"
  private val externalBaseURL = s"http://$host:$port"

  val httpServerConfigTest: HTTPServerConfig =
    HTTPServerConfig(host, port, externalBaseURL)
}
