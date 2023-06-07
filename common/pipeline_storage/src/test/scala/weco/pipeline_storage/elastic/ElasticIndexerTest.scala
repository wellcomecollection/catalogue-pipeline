package weco.pipeline_storage.elastic

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.{ElasticClient, Index, Response}
import com.sksamuel.elastic4s.requests.get.GetResponse
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.scalatest.{Assertion, EitherValues}
import weco.catalogue.internal_model.fixtures.index.IndexFixtures
import weco.elasticsearch.ElasticHttpClientConfig
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.catalogue.internal_model.matchers.EventuallyInElasticsearch
import weco.json.utils.JsonAssertions
import weco.pipeline_storage.generators.{
  SampleDocument,
  SampleDocumentData,
  SampleDocumentGenerators
}
import weco.pipeline_storage.{Indexer, IndexerTestCases}

import scala.concurrent.ExecutionContext.Implicits.global

class ElasticIndexerTest
    extends IndexerTestCases[Index, SampleDocument]
    with IndexFixtures
    with EitherValues
    with JsonAssertions
    with SampleDocumentGenerators
    with EventuallyInElasticsearch {

  import weco.pipeline_storage.generators.SampleDocument._

  override def withContext[R](
    documents: Seq[SampleDocument]
  )(testWith: TestWith[Index, R]): R =
    withLocalUnanalysedJsonStore {
      implicit index =>
        if (documents.nonEmpty) {
          withIndexer {
            indexer =>
              indexer(documents).await shouldBe a[Right[_, _]]
          }
        }

        eventually {
          val storedDocuments =
            matchAllAsDocuments[SampleDocument](index, documents.size)
          storedDocuments should contain theSameElementsAs documents
        }

        testWith(index)
    }

  override def withIndexer[R](
    testWith: TestWith[Indexer[SampleDocument], R]
  )(implicit index: Index): R = {
    val indexer = new ElasticIndexer[SampleDocument](
      client = elasticClient,
      index = index
    )
    testWith(indexer)
  }

  override def createDocument: SampleDocument =
    createDocumentWith()

  override def assertIsIndexed(
    doc: SampleDocument
  )(implicit index: Index): Assertion =
    assertElasticsearchEventuallyHas(index, doc).head

  override def assertIsNotIndexed(
    doc: SampleDocument
  )(implicit index: Index): Assertion = {
    val documentJson = toJson(doc).get

    eventually {
      val response: Response[GetResponse] = elasticClient.execute {
        get(index, SampleDocument.canonicalId.indexId(doc))
      }.await

      val getResponse = response.result

      // If there's a document with this ID, we want to make sure it's something
      // different.  If there's no document with this ID, then all is well.
      if (getResponse.exists) {
        assertJsonStringsAreDifferent(getResponse.sourceAsString, documentJson)
      } else {
        assert(true)
      }
    }
  }

  it("returns a list of documents that weren't indexed correctly") {
    val validDocuments = (1 to 5).map {
      _ =>
        createDocument
    }
    val invalidDocuments = (1 to 3).map {
      _ =>
        createDocument
          .copy(data = SampleDocumentData(genre = Some(randomAlphanumeric())))
    }

    val strictWithNoDataIndexConfig =
      """{
         |"mappings": {
         | "dynamic": "strict",
         | "properties":{
         |   "id":{ "type": "keyword" },
         |   "title":{ "type": "keyword" },
         |   "data":{ "properties": { }},
         |   "version":{ "type": "long" }
         | }
         |}
         |}
        |""".stripMargin

    withLocalElasticSearchIndex(config = strictWithNoDataIndexConfig) {
      implicit index =>
        withIndexer {
          indexer =>
            val future = indexer(validDocuments ++ invalidDocuments)

            whenReady(future) {
              result =>
                result.left.get should contain only (invalidDocuments: _*)

                validDocuments.foreach {
                  doc =>
                    assertIsIndexed(doc)
                }

                invalidDocuments.foreach {
                  doc =>
                    assertIsNotIndexed(doc)
                }
            }
        }
    }
  }

  it("does not store optional fields when those fields are unmapped") {
    val documentA = createDocumentWith("A", 1).copy(
      data = SampleDocumentData(genre = Some("Crime"))
    )

    val documentB = createDocumentWith("B", 2).copy(
      data = SampleDocumentData(date = Some("10/10/2010"))
    )

    val documents = List(documentA, documentB)

    val unmappedDataMappingIndexConfig =
      """{
        |"mappings": {
        | "dynamic": "strict",
        | "properties":{
        |   "id":{ "type": "keyword" },
        |   "title":{ "type": "keyword" },
        |   "data":{ "dynamic": false, "properties": { }},
        |   "version":{ "type": "long" }
        | }
        |}
        |}
        |""".stripMargin

    withLocalElasticSearchIndex(config = unmappedDataMappingIndexConfig) {
      implicit index =>
        withIndexer {
          indexer =>
            val future = indexer(documents)

            whenReady(future) {
              result =>
                result.right.get should contain only (documents: _*)
                matchAllAsMaps(index, 2) shouldBe List(
                  Map(
                    "id" -> documentA.id,
                    "version" -> documentA.version,
                    "title" -> documentA.title,
                    "data" -> Map("genre" -> "Crime")
                  ),
                  Map(
                    "id" -> documentB.id,
                    "version" -> documentB.version,
                    "title" -> documentB.title,
                    "data" -> Map("date" -> "10/10/2010")
                  )
                )
            }
        }
    }
  }

  it("returns a failed future if indexing an empty list of ids") {
    withContext() {
      implicit context =>
        withIndexer {
          indexer =>
            val future = indexer(Seq())

            whenReady(future.failed) {
              _ shouldBe a[IllegalArgumentException]
            }
        }
    }
  }

  describe("handles documents that are too big to index in one request") {

    // These tests depend on the exact size of the document we send to Elasticsearch,
    // and compression makes that non-deterministic -- how well a document compresses
    // will vary between invocations.
    //
    // To avoid flakiness, we disable compression for these tests and these tests only.

    def withNoCompressionIndexer[R](
      testWith: TestWith[Indexer[SampleDocument], R]
    )(implicit index: Index): R = {
      val restClient = RestClient
        .builder(new HttpHost("localhost", 9200, "http"))
        .setHttpClientConfigCallback(
          new ElasticHttpClientConfig("elastic", "changeme", None)
        )
        .build()

      val elasticClient = ElasticClient(JavaClient.fromRestClient(restClient))

      val indexer = new ElasticIndexer[SampleDocument](
        client = elasticClient,
        index = index
      )

      testWith(indexer)
    }

    it("indexes a lot of small documents that add up to something big") {
      // This collection has to exceed the ``http.max_content_length`` setting
      // in Elasticsearch.  If that happens, we get a 413 Request Too Large error.
      //
      // The default value of the setting is 100mb; to avoid queuing up that many
      // documents in this test, we've turned the limit down to 1mb in the
      // Docker Compose file for these tests.
      val title = randomAlphanumeric(length = 20000)
      val documents = (1 to 100)
        .map {
          _ =>
            createDocument.copy(title = title)
        }

      withContext() {
        implicit index: Index =>
          withNoCompressionIndexer {
            indexer =>
              val future = indexer(documents)

              whenReady(future) {
                resp =>
                  resp shouldBe a[Right[_, _]]
                  resp.right.value should contain theSameElementsAs documents
              }

              // Because Elasticsearch isn't strongly consistent, it may take a
              // few seconds for the count response to be accurate.
              shouldHaveCount(index, documents.size)
          }
      }
    }

    it("fails to index a single big document") {
      val title = randomAlphanumeric(length = 2000000)
      val documents = Seq(createDocument.copy(title = title))

      withContext() {
        implicit index: Index =>
          withNoCompressionIndexer {
            indexer =>
              val future = indexer(documents)

              whenReady(future) {
                _.left.value shouldBe documents
              }
          }
      }
    }

    it("fails to index two big documents") {
      val title = randomAlphanumeric(length = 2000000)
      val documents = Seq(
        createDocument.copy(title = title),
        createDocument.copy(title = title)
      )

      withContext() {
        implicit index: Index =>
          withNoCompressionIndexer {
            indexer =>
              val future = indexer(documents)

              whenReady(future) {
                _.left.value shouldBe documents
              }
          }
      }
    }

    val smallDocument = createDocument
    val bigDocument =
      createDocument.copy(title = randomAlphanumeric(length = 2000000))

    it(
      "indexes everything except the single big document (big document last)"
    ) {
      val documents = Seq(smallDocument, bigDocument)

      withContext() {
        implicit index: Index =>
          withIndexer {
            indexer =>
              val future = indexer(documents)

              whenReady(future) {
                _.left.value shouldBe Seq(bigDocument)
              }

              assertElasticsearchEventuallyHas(index, smallDocument)
          }
      }
    }

    it(
      "indexes everything except the single big document (big document first)"
    ) {
      val documents = Seq(bigDocument, smallDocument)

      withContext() {
        implicit index: Index =>
          withIndexer {
            indexer =>
              val future = indexer(documents)

              whenReady(future) {
                _.left.value shouldBe Seq(bigDocument)
              }

              assertElasticsearchEventuallyHas(index, smallDocument)
          }
      }
    }
  }
}
