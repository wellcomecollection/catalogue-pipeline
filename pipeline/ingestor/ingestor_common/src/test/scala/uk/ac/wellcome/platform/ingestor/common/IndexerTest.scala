package uk.ac.wellcome.platform.ingestor.common

import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import com.sksamuel.elastic4s.{ElasticClient, Indexable}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.elasticsearch.IndexConfig
import uk.ac.wellcome.elasticsearch.model.CanonicalId
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class IndexerTest extends FunSpec with ScalaFutures with Matchers with IdentifiersGenerators with ElasticsearchFixtures{
  implicit val canonicalId: CanonicalId[SampleDocument] = (t: SampleDocument) => t.canonicalId
  case class SampleDocument(version: Int, canonicalId: String, title: String)

  val indexer = new Indexer[SampleDocument]{
    override val client: ElasticClient = elasticClient
    override implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    override implicit val indexable: Indexable[SampleDocument] = (t: SampleDocument) => toJson(t).get
    override implicit val id: CanonicalId[SampleDocument] = (t: SampleDocument) => t.canonicalId

    override def calculateEsVersion(t: SampleDocument): Int = t.version
  }

  it("inserts an identified Work into Elasticsearch") {
    val document = SampleDocument(1, createCanonicalId, randomAlphanumeric(10))

    withLocalElasticsearchIndex(config = NoStrictMapping) { index =>
      val future = indexer.index(Seq(document), index)

      whenReady(future) { result =>
        result.right.get should contain(document)
        assertElasticsearchEventuallyHas(index = index, document)
      }
    }
  }

  it("only adds one record when the same ID is ingested multiple times") {
    val document = SampleDocument(1, createCanonicalId, randomAlphanumeric(10))

    withLocalElasticsearchIndex(config = NoStrictMapping) { index =>
      val future = Future.sequence(
        (1 to 2).map(_ => indexer.index(Seq(document), index))
      )

      whenReady(future) { _ =>
        assertElasticsearchEventuallyHas(index = index, document)
      }
    }
  }

  it("doesn't add a Work with a lower version") {
    val document = SampleDocument(3, createCanonicalId, randomAlphanumeric(10))
    val olderDocument = document.copy(version = 1)

    withLocalElasticsearchIndex(config = NoStrictMapping) { index =>
      val future = for {
        _ <- indexer.index(Seq(document), index)
        result <- indexer.index(Seq(olderDocument), index)
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

    withLocalElasticsearchIndex(config = NoStrictMapping) { index =>
      val future = for {
        _ <- indexer.index(Seq(document), index)
        result <- indexer.index(Seq(updatedDocument), index)
      } yield result

      whenReady(future) { result =>
        result.right.get should contain(updatedDocument)
        assertElasticsearchEventuallyHas(index = index, updatedDocument)
      }
    }
  }

  it("inserts a list of works into elasticsearch and returns them") {
    val documents = (1 to 5).map(_ => SampleDocument(1, createCanonicalId, randomAlphanumeric(10)))

    withLocalElasticsearchIndex(config = NoStrictMapping) { index =>
      val future = indexer.index(documents, index)

      whenReady(future) { successfullyInserted =>
        assertElasticsearchEventuallyHas(index = index, documents: _*)
        successfullyInserted.right.get should contain theSameElementsAs documents
      }
    }
  }

  object NoStrictMapping extends IndexConfig {
    import uk.ac.wellcome.elasticsearch.WorksIndexConfig.{analysis => defaultAnalysis}

    val analysis = defaultAnalysis
    val mapping = MappingDefinition.empty
  }
}
