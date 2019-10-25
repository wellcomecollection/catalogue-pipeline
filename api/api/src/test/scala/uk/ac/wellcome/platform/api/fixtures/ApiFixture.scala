package uk.ac.wellcome.platform.api.fixtures

import org.scalatest.FunSpec
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.model.{ContentTypes, StatusCode}
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.server.Route
import io.circe.parser.parse
import io.circe.Json
import com.sksamuel.elastic4s.Index

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.api.Router
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.elasticsearch.DisplayElasticConfig
import uk.ac.wellcome.platform.api.models.ApiConfig

trait ApiFixture
    extends FunSpec
    with ScalatestRouteTest
    with ElasticsearchFixtures {

  val Status = akka.http.scaladsl.model.StatusCodes

  val apiScheme: String
  val apiHost: String
  val apiName: String

  implicit def defaultHostInfo = DefaultHostInfo(
    host = Host(apiHost),
    securedConnection = if (apiScheme == "https") true else false
  )

  def withApi[R](testWith: TestWith[(Index, Route), R]): R =
    withLocalWorksIndex { indexV2 =>
      val router = new Router(
        elasticClient,
        DisplayElasticConfig(indexV2 = indexV2),
        ApiConfig(
          host = apiHost,
          scheme = apiScheme,
          defaultPageSize = 10,
          pathPrefix = apiName,
          contextSuffix = "context.json"
        )
      )
      testWith((indexV2, router.routes))
    }

  def assertJsonResponse(routes: Route, path: String)(
    expectedResponse: (StatusCode, String)) =
    eventually {
      expectedResponse match {
        case (expectedStatus, expectedJson) =>
          Get(path) ~> routes ~> check {
            contentType shouldEqual ContentTypes.`application/json`
            parseJson(responseAs[String]) shouldEqual parseJson(expectedJson)
            status shouldEqual expectedStatus
          }
      }
    }

  def assertRedirectResponse(routes: Route, path: String)(
    expectedResponse: (StatusCode, String)) =
    eventually {
      expectedResponse match {
        case (expectedStatus, expectedLocation) =>
          Get(path) ~> routes ~> check {
            status shouldEqual expectedStatus
            header("Location").map(_.value) shouldEqual Some(expectedLocation)
          }
      }
    }

  def parseJson(string: String) =
    parse(string).left.map(_ => s"Invalid JSON").right.map(sortedJson)

  def sortedJson(json: Json): Json =
    json.arrayOrObject(
      json,
      array => Json.arr(array.map(sortedJson): _*),
      obj =>
        Json.obj(
          obj.toList
            .map { case (key, value) => (key, sortedJson(value)) }
            .sortBy(tup => tup._1): _*
      )
    )
}
