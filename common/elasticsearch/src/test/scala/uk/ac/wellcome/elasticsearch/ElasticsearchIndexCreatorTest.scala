package uk.ac.wellcome.elasticsearch

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterEach, FunSpec, Matchers}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{RequestFailure, Response}
import com.sksamuel.elastic4s.requests.indexes.IndexResponse
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions

case class TestObject(
  id: String,
  description: String,
  visible: Boolean
)

case class CompatibleTestObject(
  id: String,
  description: String,
  visible: Boolean,
  count: Int
)

case class BadTestObject(
  id: String,
  weight: Int
)

class ElasticsearchIndexCreatorTest
    extends FunSpec
    with ElasticsearchFixtures
    with ScalaFutures
    with Eventually
    with Matchers
    with JsonAssertions
    with BeforeAndAfterEach {

  val indexFields = Seq(
    keywordField("id"),
    textField("description"),
    booleanField("visible")
  )

  object TestIndexConfig extends IndexConfig {
    def create(name: String) =
      createIndex(name).mapping(
        properties(
          Seq(
            keywordField("id"),
            textField("description"),
            booleanField("visible")
          )))
  }

  object CompatibleTestIndexConfig extends IndexConfig {
    def create(name: String) =
      createIndex(name).mapping(
        properties(
          Seq(
            keywordField("id"),
            textField("description"),
            booleanField("visible"),
            intField("count")
          )))
  }

  it("creates an index into which doc of the expected type can be put") {
    withLocalElasticsearchIndex(createIndexName, TestIndexConfig) { index =>
      val testObject = TestObject("id", "description", visible = true)
      val testObjectJson = toJson(testObject).get

      eventually {
        for {
          _ <- elasticClient.execute(indexInto(index.name).doc(testObjectJson))
          response: Response[SearchResponse] <- elasticClient
            .execute {
              search(index).matchAllQuery()
            }
        } yield {
          val hits = response.result.hits.hits
          hits should have size 1

          assertJsonStringsAreEqual(
            hits.head.sourceAsString,
            testObjectJson
          )
        }
      }
    }
  }

  it("create an index where inserting a doc of an unexpected type fails") {
    withLocalElasticsearchIndex(createIndexName, TestIndexConfig) { index =>
      val badTestObject = BadTestObject("id", 5)
      val badTestObjectJson = toJson(badTestObject).get

      val future: Future[Response[IndexResponse]] =
        elasticClient
          .execute {
            indexInto(index.name)
              .doc(badTestObjectJson)
          }

      whenReady(future) { response =>
        response.isError shouldBe true
        response shouldBe a[RequestFailure]
      }
    }
  }

  it("updates an already existing index with a compatible mapping") {
    withLocalElasticsearchIndex(createIndexName, TestIndexConfig) { index =>
      withLocalElasticsearchIndex(createIndexName, CompatibleTestIndexConfig) {
        _ =>
          val compatibleTestObject = CompatibleTestObject(
            id = "id",
            description = "description",
            count = 5,
            visible = true
          )

          val compatibleTestObjectJson = toJson(compatibleTestObject).get

          val futureInsert: Future[Response[IndexResponse]] =
            elasticClient
              .execute {
                indexInto(index.name)
                  .doc(compatibleTestObjectJson)
              }

          whenReady(futureInsert) { response =>
            if (response.isError) { println(response) }
            response.isError shouldBe false

            eventually {
              val response: Response[SearchResponse] =
                elasticClient.execute {
                  search(index).matchAllQuery()
                }.await

              val hits = response.result.hits.hits

              hits should have size 1

              assertJsonStringsAreEqual(
                hits.head.sourceAsString,
                compatibleTestObjectJson
              )
            }
          }
      }
    }
  }
}
