package uk.ac.wellcome.platform.api.fixtures

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.model.{ContentTypes, StatusCode}
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.server.Route
import io.circe.parser.parse
import io.circe.Json
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.api.Router
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.platform.api.models.{ApiConfig, QueryConfig}

trait ApiFixture
    extends AnyFunSpec
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

  def withApi[R](testWith: TestWith[(ElasticConfig, Route), R]): R =
    withLocalIndices { elasticConfig =>
      val router = new Router(
        elasticClient,
        elasticConfig,
        QueryConfig(paletteBinSizes = Seq(4, 6, 8)),
        ApiConfig(
          host = apiHost,
          scheme = apiScheme,
          defaultPageSize = 10,
          pathPrefix = apiName,
          contextSuffix = "context.json"
        ),
      )
      testWith((elasticConfig, router.routes))
    }

  def assertJsonResponse(
    routes: Route,
    path: String,
    unordered: Boolean = false)(expectedResponse: (StatusCode, String)) =
    eventually {
      expectedResponse match {
        case (expectedStatus, expectedJson) =>
          Get(path) ~> routes ~> check {
            contentType shouldEqual ContentTypes.`application/json`
            parseJson(responseAs[String], unordered) shouldEqual parseJson(
              expectedJson,
              unordered)
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

  def parseJson(string: String, unordered: Boolean = false) =
    parse(string).left
      .map(_ => s"Invalid JSON")
      .right
      .map(sortedJson(unordered))

  def sortedJson(unordered: Boolean)(json: Json): Json =
    json.arrayOrObject(
      json,
      array => {
        val arr = array.map(sortedJson(unordered))
        if (unordered) {
          Json.arr(arr.sortBy(_.toString): _*)
        } else {
          Json.arr(arr: _*)
        }
      },
      obj =>
        Json.obj(
          obj.toList
            .map {
              case (key, value) =>
                (key, sortedJson(unordered)(value))
            }
            .sortBy(tup => tup._1): _*
      )
    )
}
