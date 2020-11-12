package uk.ac.wellcome.platform.api.fixtures

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.model.{ContentTypes, StatusCode}
import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.server.Route
import com.sksamuel.elastic4s.Index
import io.circe.parser.parse
import io.circe.Json
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.elasticsearch.ElasticConfig
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.api.Router
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.platform.api.models.{ApiConfig, QueryConfig}
import uk.ac.wellcome.platform.api.swagger.SwaggerDocs

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

  lazy val apiConfig = ApiConfig(
    host = apiHost,
    scheme = apiScheme,
    defaultPageSize = 10,
    pathPrefix = apiName,
    contextSuffix = "context.json"
  )

  // Note: creating new instances of the SwaggerDocs class is expensive, so
  // we cache it and reuse it between test instances to reduce the number
  // of times we have to create it.
  lazy val swaggerDocs = new SwaggerDocs(apiConfig)

  private def withRouter[R](elasticConfig: ElasticConfig)(
    testWith: TestWith[Route, R]): R = {
    val router = new Router(
      elasticClient,
      elasticConfig,
      QueryConfig(paletteBinSizes = Seq(4, 6, 8)),
      swaggerDocs = swaggerDocs,
      apiConfig = apiConfig
    )

    testWith(router.routes)
  }

  def withApi[R](testWith: TestWith[Route, R]): R = {
    val elasticConfig = ElasticConfig(
      worksIndex = Index("worksIndex-notused"),
      imagesIndex = Index("imagesIndex-notused")
    )

    withRouter(elasticConfig) { route =>
      testWith(route)
    }
  }

  def withWorksApi[R](testWith: TestWith[(Index, Route), R]): R =
    withLocalWorksIndex { worksIndex =>
      val elasticConfig = ElasticConfig(
        worksIndex = worksIndex,
        imagesIndex = Index("imagesIndex-notused")
      )

      withRouter(elasticConfig) { route =>
        testWith((worksIndex, route))
      }
    }

  def withImagesApi[R](testWith: TestWith[(Index, Route), R]): R =
    withLocalImagesIndex { imagesIndex =>
      val elasticConfig = ElasticConfig(
        worksIndex = Index("worksIndex-notused"),
        imagesIndex = imagesIndex
      )

      withRouter(elasticConfig) { route =>
        testWith((imagesIndex, route))
      }
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

  def parseJson(string: String, unordered: Boolean = false): Json =
    parse(string) match {
      case Right(json) => sortedJson(unordered)(json)
      case Left(err) =>
        throw new RuntimeException(
          s"Asked to compare a string that wasn't JSON. Error: $err. JSON:\n$string"
        )
    }

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
