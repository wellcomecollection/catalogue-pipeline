package uk.ac.wellcome.pipeline_storage.elastic

import com.sksamuel.elastic4s.Index
import uk.ac.wellcome.elasticsearch.NoStrictMapping
import uk.ac.wellcome.elasticsearch.model.CanonicalId
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.pipeline_storage.{
  ElasticRetriever,
  Retriever,
  RetrieverTestCases
}
import uk.ac.wellcome.pipeline_storage.fixtures.{
  ElasticIndexerFixtures,
  SampleDocument
}

import scala.concurrent.ExecutionContext.Implicits.global

class ElasticRetrieverTest
  extends RetrieverTestCases[Index, SampleDocument]
    with ElasticsearchFixtures
    with ElasticIndexerFixtures
    with IdentifiersGenerators {

  import SampleDocument._

  override def withContext[R](documents: Seq[SampleDocument])(
    testWith: TestWith[Index, R]): R =
    withLocalElasticsearchIndex(config = NoStrictMapping) { index =>
      withElasticIndexer[SampleDocument, R](index) { indexer =>
        whenReady(indexer.index(documents)) { _ =>
          assertElasticsearchEventuallyHas(index, documents: _*)

          testWith(index)
        }
      }
    }

  override def withRetriever[R](
                                 testWith: TestWith[Retriever[SampleDocument], R])(
                                 implicit index: Index): R =
    testWith(
      new ElasticRetriever(elasticClient, index = index)
    )

  override def createT: SampleDocument =
    SampleDocument(
      version = 1,
      canonicalId = createCanonicalId,
      title = randomAlphanumeric()
    )

  override implicit val id: CanonicalId[SampleDocument] =
    (doc: SampleDocument) => doc.canonicalId

  it("retrieves a document with a slash in the ID") {
    val documentWithSlash = SampleDocument(
      version = 1,
      canonicalId = "sierra-system-number/b1234",
      title = randomAlphanumeric()
    )

    withContext(documents = Seq(documentWithSlash)) { implicit context =>
      val future = withRetriever { _.apply(documentWithSlash.canonicalId) }

      whenReady(future) {
        _ shouldBe documentWithSlash
      }
    }
  }
}
