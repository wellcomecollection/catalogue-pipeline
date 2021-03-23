package uk.ac.wellcome.pipeline_storage.elastic

import com.sksamuel.elastic4s.Index
import uk.ac.wellcome.elasticsearch.NoStrictMapping
import uk.ac.wellcome.elasticsearch.model.IndexId
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.pipeline_storage.{Retriever, RetrieverTestCases}
import uk.ac.wellcome.pipeline_storage.fixtures.ElasticIndexerFixtures
import weco.catalogue.pipeline_storage.generators.{
  SampleDocument,
  SampleDocumentGenerators
}

import scala.concurrent.ExecutionContext.Implicits.global

class ElasticSourceRetrieverTest
    extends RetrieverTestCases[Index, SampleDocument]
    with ElasticIndexerFixtures
    with SampleDocumentGenerators {

  import SampleDocument._

  override def withContext[R](documents: Seq[SampleDocument])(
    testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex(config = NoStrictMapping) { index =>
      withElasticIndexer[SampleDocument, R](index) { indexer =>
        whenReady(indexer(documents)) { _ =>
          assertElasticsearchEventuallyHas(index, documents: _*)

          testWith(index)
        }
      }
    }

  override def withRetriever[R](
    testWith: TestWith[Retriever[SampleDocument], R])(
    implicit index: Index): R =
    testWith(
      new ElasticSourceRetriever(elasticClient, index)
    )

  override def createT: SampleDocument =
    createSampleDocument

  override implicit val id: IndexId[SampleDocument] =
    (doc: SampleDocument) => doc.canonicalId

  it("retrieves a document with a slash in the ID") {
    val documentWithSlash = createSampleDocumentWith(
      canonicalId = "sierra-system-number/b1234"
    )

    withContext(documents = Seq(documentWithSlash)) { implicit context =>
      val future = withRetriever { _.apply(documentWithSlash.canonicalId) }

      whenReady(future) {
        _ shouldBe documentWithSlash
      }
    }
  }

  it("fails if asking for an empty list of ids") {
    withContext(Seq(createT)) { implicit context =>
      val future = withRetriever { _.apply(List()) }

      whenReady(future.failed) {
        _ shouldBe a[IllegalArgumentException]
      }
    }
  }
}
