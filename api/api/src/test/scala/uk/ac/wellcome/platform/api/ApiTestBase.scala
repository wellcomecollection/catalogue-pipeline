package uk.ac.wellcome.platform.api

import com.sksamuel.elastic4s.{ElasticDsl, Index}
import com.sksamuel.elastic4s.ElasticDsl._
import org.scalatest.Assertion
import uk.ac.wellcome.display.models.ApiVersions
import uk.ac.wellcome.fixtures._
import uk.ac.wellcome.platform.api.fixtures.ApiFixture

trait ApiTestBase extends ApiFixture with RandomGenerators {
  def getApiPrefix(
    apiVersion: ApiVersions.Value = ApiVersions.default): String =
    apiName + "/" + apiVersion

  val apiScheme = "https"
  val apiHost = "api-testing.local"
  val apiName = "catalogue"
  val apiPrefix: String = getApiPrefix(ApiVersions.v2)

  def contextUrl(apiPrefix: String): String =
    s"$apiScheme://$apiHost/$apiPrefix/context.json"

  def emptyJsonResult(apiPrefix: String): String =
    s"""
      |{
      |  ${resultList(apiPrefix, totalPages = 0, totalResults = 0)},
      |  "results": []
      |}""".stripMargin

  def badRequest(apiPrefix: String, description: String) =
    s"""{
      "@context": "${contextUrl(apiPrefix)}",
      "type": "Error",
      "errorType": "http",
      "httpStatus": 400,
      "label": "Bad Request",
      "description": "$description"
    }"""

  def goneRequest(apiPrefix: String, description: String) =
    s"""{
      "@context": "${contextUrl(apiPrefix)}",
      "type": "Error",
      "errorType": "http",
      "httpStatus": 410,
      "label": "Gone",
      "description": "$description"
    }"""

  def resultList(apiPrefix: String,
                 pageSize: Int = 10,
                 totalPages: Int = 1,
                 totalResults: Int) =
    s"""
      "@context": "${contextUrl(apiPrefix)}",
      "type": "ResultList",
      "pageSize": $pageSize,
      "totalPages": $totalPages,
      "totalResults": $totalResults
    """

  def notFound(apiPrefix: String, description: String) =
    s"""{
      "@context": "${contextUrl(apiPrefix)}",
      "type": "Error",
      "errorType": "http",
      "httpStatus": 404,
      "label": "Not Found",
      "description": "$description"
    }"""

  def deleted(apiPrefix: String) =
    s"""{
      "@context": "${contextUrl(apiPrefix)}",
      "type": "Error",
      "errorType": "http",
      "httpStatus": 410,
      "label": "Gone",
      "description": "This work has been deleted"
    }"""

  def withEmptyIndex[R]: Fixture[Index, R] =
    fixture[Index, R](
      create = {
        val index = createIndex
        elasticClient
          .execute {
            ElasticDsl.createIndex(index.name)
          }
        eventuallyIndexExists(index)
        index
      },
      destroy = eventuallyDeleteIndex
    )

  def assertIsBadRequest(path: String, description: String): Assertion =
    withWorksApi {
      case (_, routes) =>
        assertJsonResponse(routes, s"/$apiPrefix$path")(
          Status.BadRequest ->
            badRequest(
              apiPrefix = apiPrefix,
              description = description
            )
        )
    }
}
