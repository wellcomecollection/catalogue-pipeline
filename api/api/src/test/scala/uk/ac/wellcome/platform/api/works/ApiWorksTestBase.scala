package uk.ac.wellcome.platform.api.works

import com.sksamuel.elastic4s.{Index, Indexable}
import com.sksamuel.elastic4s.ElasticDsl._
import com.twitter.finatra.http.EmbeddedHttpServer
import org.scalatest.FunSpec
import uk.ac.wellcome.display.models.ApiVersions
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.generators.{WorksGenerators, GenreGenerators}
import uk.ac.wellcome.models.work.internal.IdentifiedWork
import uk.ac.wellcome.platform.api.Server

import scala.concurrent.ExecutionContext.Implicits.global

trait ApiWorksTestBase
    extends FunSpec
    with ElasticsearchFixtures
    with WorksGenerators
    with GenreGenerators {

  implicit object IdentifiedWorkIndexable extends Indexable[IdentifiedWork] {
    override def json(t: IdentifiedWork): String =
      toJson(t).get
  }

  val apiScheme: String = "https"
  val apiHost: String = "api-testing.local"

  def withServer[R](indexV1: Index, indexV2: Index)(
    testWith: TestWith[EmbeddedHttpServer, R]): R = {

    val server: EmbeddedHttpServer = new EmbeddedHttpServer(
      new Server,
      flags = displayEsLocalFlags(
        indexV1 = indexV1,
        indexV2 = indexV2
      ) ++ Map(
        "api.scheme" -> apiScheme,
        "api.host" -> apiHost
      )
    )

    server.start()

    try {
      testWith(server)
    } finally {
      server.close()
    }
  }

  val apiName = "catalogue/"

  def getApiPrefix(
    apiVersion: ApiVersions.Value = ApiVersions.default): String =
    apiName + apiVersion

  def withApi[R](testWith: TestWith[(Index, Index, EmbeddedHttpServer), R]): R =
    withLocalWorksIndex { indexV1 =>
      withLocalWorksIndex { indexV2 =>
        withServer(indexV1, indexV2) { server =>
          testWith((indexV1, indexV2, server))
        }
      }
    }

  def withHttpServer[R](testWith: TestWith[EmbeddedHttpServer, R]): R =
    withServer(Index("index-v1"), Index("index-v2")) { server =>
      testWith(server)
    }

  def contextUrl(apiPrefix: String): String =
    s"$apiScheme://$apiHost/$apiPrefix/context.json"

  def emptyJsonResult(apiPrefix: String): String = s"""
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
                 totalResults: Int = 1) =
    s"""
      "@context": "${contextUrl(apiPrefix)}",
      "type": "ResultList",
      "pageSize": $pageSize,
      "totalPages": $totalPages,
      "totalResults": $totalResults
    """

  def singleWorkResult(apiPrefix: String): String =
    s"""
        "@context": "${contextUrl(apiPrefix)}",
        "type": "Work"
     """.stripMargin

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
        val index = Index(randomAlphanumeric(length = 10))
        elasticClient
          .execute {
            createIndex(index.name)
          }
        eventuallyIndexExists(index)
        index
      },
      destroy = eventuallyDeleteIndex
    )
}
