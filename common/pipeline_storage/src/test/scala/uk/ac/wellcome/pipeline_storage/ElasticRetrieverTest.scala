package uk.ac.wellcome.pipeline_storage

import scala.concurrent.ExecutionContext.Implicits.global

import com.sksamuel.elastic4s.Index
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import io.circe.Encoder

import uk.ac.wellcome.elasticsearch.IndexConfig
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.pipeline_storage.fixtures.{
  ElasticIndexerFixtures,
  SampleDocument,
}

class ElasticRetrieverTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with IdentifiersGenerators
    with ElasticsearchFixtures
    with ElasticIndexerFixtures {

  import SampleDocument._

  it("retrieves a document from Elasticsearch") {
    val document = SampleDocument(1, createCanonicalId, "document")

    withIndexAndIndexer[SampleDocument, Any]() {
      case (index, indexer) =>
        val future = indexer.index(Seq(document))

        val retriever = new ElasticRetriever(elasticClient, index)

        whenReady(future) { result =>
          result.right.get should contain(document)
          assertElasticsearchEventuallyHas(index = index, document)

          whenReady(retriever(document.canonicalId)) { result =>
            result shouldBe document
          }
        }
    }
  }

  def withIndexAndIndexer[T, R](config: IndexConfig = NoStrictMapping)(
    testWith: TestWith[(Index, Indexer[T]), R])(implicit encoder: Encoder[T],
                                                indexable: Indexable[T]) =
    withLocalElasticsearchIndex(config) { index =>
      withElasticIndexer[T, R](index) { indexer =>
        testWith((index, indexer))
      }
    }
}
