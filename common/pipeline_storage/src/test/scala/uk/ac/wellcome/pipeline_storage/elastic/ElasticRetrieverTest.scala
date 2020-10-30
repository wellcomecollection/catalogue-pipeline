package uk.ac.wellcome.pipeline_storage.elastic

import com.sksamuel.elastic4s.Index
import io.circe.DecodingFailure
import uk.ac.wellcome.elasticsearch.NoStrictMapping
import uk.ac.wellcome.elasticsearch.model.CanonicalId
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.pipeline_storage.{
  ElasticRetriever,
  Indexable,
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
    SampleDocument.canonicalId

  it("retrieves a document with a slash in the ID") {
    val documentWithSlash = SampleDocument(
      version = 1,
      canonicalId = "sierra-system-number/b1234",
      title = randomAlphanumeric()
    )

    withContext(documents = Seq(documentWithSlash)) { implicit context =>
      val future = withRetriever {
        _.lookupSingleId(documentWithSlash.canonicalId)
      }

      whenReady(future) {
        _ shouldBe documentWithSlash
      }
    }
  }

  describe("fails if there's an error from Elasticsearch") {
    case class Person(id: String, name: String)
    case class Country(id: String, capital: String)

    implicit val personIndexable: Indexable[Person] = new Indexable[Person] {
      override def id(p: Person): String = p.id

      override def version(p: Person): Long = 1
    }

    implicit val countryIndexable: Indexable[Country] = new Indexable[Country] {
      override def id(c: Country): String = c.id

      override def version(c: Country): Long = 1
    }

    it("if it can't decode a document (single lookup)") {
      val person = Person(id = "1", name = "henry")

      withLocalElasticsearchIndex(config = NoStrictMapping) { index =>
        withElasticIndexer[Person, Any](index) { indexer =>
          whenReady(indexer.index(person)) {
            _ shouldBe a[Right[_, _]]
          }
        }

        val retriever =
          new ElasticRetriever[Country](elasticClient, index = index)

        whenReady(retriever.lookupSingleId(person.id).failed) {
          _ shouldBe a[DecodingFailure]
        }
      }
    }

    it("if it can't decode a document (multi lookup)") {
      val person = Person(id = "1", name = "henry")
      val country = Country(id = "2", capital = "london")

      withLocalElasticsearchIndex(config = NoStrictMapping) { index =>
        withElasticIndexer[Person, Any](index) { indexer =>
          whenReady(indexer.index(person)) {
            _ shouldBe a[Right[_, _]]
          }
        }

        withElasticIndexer[Country, Any](index) { indexer =>
          whenReady(indexer.index(country)) {
            _ shouldBe a[Right[_, _]]
          }
        }

        val retriever =
          new ElasticRetriever[Country](elasticClient, index = index)

        whenReady(
          retriever.lookupMultipleIds(Seq(person.id, country.id)).failed) {
          err =>
            err shouldBe a[RuntimeException]
            err.getMessage should startWith("Unable to decode some responses:")
        }
      }
    }
  }
}
