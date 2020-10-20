package uk.ac.wellcome.pipeline_storage.elastic

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{Index, Response}
import com.sksamuel.elastic4s.requests.get.GetResponse
import org.scalatest.Assertion
import uk.ac.wellcome.elasticsearch.NoStrictMapping
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil.toJson
import uk.ac.wellcome.pipeline_storage.fixtures.SampleDocument
import uk.ac.wellcome.pipeline_storage.{ElasticIndexer, Indexer, IndexerTestCases}

import scala.concurrent.ExecutionContext.Implicits.global

class ElasticIndexerTest
  extends IndexerTestCases[Index, SampleDocument]
    with ElasticsearchFixtures {

  import SampleDocument._

  override def withContext[R](documents: Seq[SampleDocument])(testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex(config = NoStrictMapping) { index =>
      documents.foreach { doc =>
        indexObject(index, doc)
        assertObjectIndexed(index, doc)
      }

      testWith(index)
    }

  override def withIndexer[R](
    testWith: TestWith[Indexer[SampleDocument], R])(implicit index: Index): R = {
    val indexer = new ElasticIndexer[SampleDocument](
      client = elasticClient,
      index = index,
      config = NoStrictMapping
    )

    testWith(indexer)
  }

  override def createDocumentWith(id: String, version: Int): SampleDocument =
    SampleDocument(canonicalId = id, version = version, title = randomAlphanumeric())

  override def assertIsIndexed(doc: SampleDocument)(implicit index: Index): Assertion =
    assertElasticsearchEventuallyHas(index, doc).head

  override def assertIsNotIndexed(doc: SampleDocument)(implicit index: Index): Assertion = {
    val documentJson = toJson(doc).get

    eventually {
      val response: Response[GetResponse] = elasticClient.execute {
        get(index, canonicalId.canonicalId(doc))
      }.await

      val getResponse = response.result

      getResponse.exists shouldBe true

      assertJsonStringsAreDifferent(getResponse.sourceAsString, documentJson)
    }
  }
}
