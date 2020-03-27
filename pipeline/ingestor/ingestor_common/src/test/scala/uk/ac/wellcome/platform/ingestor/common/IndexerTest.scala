package uk.ac.wellcome.platform.ingestor.common

import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import com.sksamuel.elastic4s.{Index, Indexable}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.elasticsearch.IndexConfig
import uk.ac.wellcome.elasticsearch.model.{CanonicalId, Version}
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.platform.ingestor.common.fixtures.{IngestorFixtures, SampleDocument}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class IndexerTest extends FunSpec with ScalaFutures with Matchers with IdentifiersGenerators with ElasticsearchFixtures with IngestorFixtures{

  it("inserts an identified Work into Elasticsearch") {
    val document = SampleDocument(1, createCanonicalId, randomAlphanumeric(10))

    withIndexAndIndexer[SampleDocument, Any]() { case (index, indexer) =>
      val future = indexer.index(Seq(document))

      whenReady(future) { result =>
        result.right.get should contain(document)
        assertElasticsearchEventuallyHas(index = index, document)
      }
    }
  }

  it("only adds one record when the same ID is ingested multiple times") {
    val document = SampleDocument(1, createCanonicalId, randomAlphanumeric(10))

    withIndexAndIndexer[SampleDocument, Any]() { case (index, indexer) =>
      val future = Future.sequence(
        (1 to 2).map(_ => indexer.index(Seq(document)))
      )

      whenReady(future) { _ =>
        assertElasticsearchEventuallyHas(index = index, document)
      }
    }
  }

  it("doesn't add a Work with a lower version") {
    val document = SampleDocument(3, createCanonicalId, randomAlphanumeric(10))
    val olderDocument = document.copy(version = 1)

    withIndexAndIndexer[SampleDocument, Any]() { case (index, indexer) =>
      val future = for {
        _ <- indexer.index(Seq(document))
        result <- indexer.index(Seq(olderDocument))
      } yield result

      whenReady(future) { result =>
        result.isRight shouldBe true
        assertElasticsearchEventuallyHas(index = index, document)
      }
    }
  }

  it("replaces a Work with the same version") {
    val document = SampleDocument(3, createCanonicalId, randomAlphanumeric(10))
    val updatedDocument = document.copy(
      title = "A different title"
    )

    withIndexAndIndexer[SampleDocument, Any]() { case (index, indexer) =>
      val future = for {
        _ <- indexer.index(Seq(document))
        result <- indexer.index(Seq(updatedDocument))
      } yield result

      whenReady(future) { result =>
        result.right.get should contain(updatedDocument)
        assertElasticsearchEventuallyHas(index = index, updatedDocument)
      }
    }
  }

  it("inserts a list of works into elasticsearch and returns them") {
    val documents = (1 to 5).map(_ => SampleDocument(1, createCanonicalId, randomAlphanumeric(10)))

    withIndexAndIndexer[SampleDocument, Any]() { case (index, indexer) =>
      val future = indexer.index(documents)

      whenReady(future) { successfullyInserted =>
        assertElasticsearchEventuallyHas(index = index, documents: _*)
        successfullyInserted.right.get should contain theSameElementsAs documents
      }
    }
  }

  it("returns a list of Works that weren't indexed correctly") {
    val validDocuments = (1 to 5).map(_ => SampleDocument(1, createCanonicalId, randomAlphanumeric))
    val notMatchingMappingDocuments = (1 to 3).map(_ =>SampleDocument(1, createCanonicalId, randomAlphanumeric, Some("blah bluh blih")))
    val documents = validDocuments ++ notMatchingMappingDocuments

    withIndexAndIndexer[SampleDocument, Any](config = StrictWithNoDataIndexConfig) { case (index, indexer) =>
        val future = indexer.index(
          documents = documents
        )

        whenReady(future) { result =>
          assertElasticsearchEventuallyHas(index = index, validDocuments: _*)
          assertElasticsearchNeverHas[SampleDocument](index = index, notMatchingMappingDocuments:_*)
          result.left.get should contain only (notMatchingMappingDocuments)
        }
    }
  }

  def withIndexAndIndexer[T, R](config: IndexConfig = NoStrictMapping)(testWith: TestWith[(Index,Indexer[T]), R])(implicit i: Indexable[T], c: CanonicalId[T], v: Version[T]) = {
    withLocalElasticsearchIndex(config){ index =>
      withIndexer[T,R](index) { indexer =>
        testWith((index,indexer))
      }
    }
  }


  object StrictWithNoDataIndexConfig extends IndexConfig {
    import com.sksamuel.elastic4s.ElasticDsl._
    import uk.ac.wellcome.elasticsearch.WorksIndexConfig.{analysis => defaultAnalysis}

    val analysis = defaultAnalysis

    val title = textField("title")
    val canonicalId = keywordField("canonicalId")
    val version = intField("version")

    val mapping = properties(Seq(title, canonicalId, version)).dynamic(DynamicMapping.Strict)
  }
}
