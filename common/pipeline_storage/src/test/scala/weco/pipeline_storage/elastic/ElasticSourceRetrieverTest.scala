package weco.pipeline_storage.elastic

import com.sksamuel.elastic4s.Index
import weco.catalogue.internal_model.fixtures.index.IndexFixturesBase
import weco.catalogue.internal_model.matchers.EventuallyInElasticsearch
import weco.elasticsearch.model.IndexId
import weco.fixtures.TestWith
import weco.pipeline_storage.Retriever
import weco.pipeline_storage.generators.SampleDocument
import weco.pipeline_storage.{Retriever, RetrieverTestCases}
import weco.pipeline_storage.fixtures.ElasticIndexerFixtures
import weco.pipeline_storage.generators.{
  SampleDocument,
  SampleDocumentGenerators
}

import scala.concurrent.ExecutionContext.Implicits.global

class ElasticSourceRetrieverTest
    extends RetrieverTestCases[Index, SampleDocument]
    with ElasticIndexerFixtures
    with EventuallyInElasticsearch
    with IndexFixturesBase
    with SampleDocumentGenerators {

  import weco.pipeline_storage.generators.SampleDocument._

  override def withContext[R](
    documents: Seq[SampleDocument]
  )(testWith: TestWith[Index, R]): R =
    withLocalUnanalysedJsonStore {
      index =>
        withElasticIndexer[SampleDocument, R](index) {
          indexer =>
            whenReady(indexer(documents)) {
              _ =>
                assertElasticsearchEventuallyHas(index, documents: _*)

                testWith(index)
            }
        }
    }

  override def withRetriever[R](
    testWith: TestWith[Retriever[SampleDocument], R]
  )(implicit index: Index): R =
    testWith(
      new ElasticSourceRetriever(elasticClient, index)
    )

  override def createT: SampleDocument =
    createDocument

  override implicit val id: IndexId[SampleDocument] =
    (doc: SampleDocument) => doc.id

  it("retrieves a document with a slash in the ID") {
    val documentWithSlash = createDocumentWith(
      id = "sierra-system-number/b1234"
    )

    withContext(documents = Seq(documentWithSlash)) {
      implicit context =>
        val future = withRetriever { _.apply(documentWithSlash.id) }

        whenReady(future) {
          _ shouldBe documentWithSlash
        }
    }
  }

  it("fails if asking for an empty list of ids") {
    withContext(Seq(createT)) {
      implicit context =>
        val future = withRetriever { _.apply(List()) }

        whenReady(future.failed) {
          _ shouldBe a[IllegalArgumentException]
        }
    }
  }
}
