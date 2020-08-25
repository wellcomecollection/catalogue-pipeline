package uk.ac.wellcome.pipeline_storage.fixtures

import scala.concurrent.ExecutionContext

import com.sksamuel.elastic4s.requests.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import com.sksamuel.elastic4s.{ElasticClient, Index}
import org.scalatest.Suite
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.elasticsearch.IndexConfig
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.pipeline_storage.{ElasticIndexer, Indexable}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.elasticsearch.model.CanonicalId

case class SampleDocument(version: Int,
                          canonicalId: String,
                          title: String,
                          data: SampleDocumentData = SampleDocumentData())
case class SampleDocumentData(stuff: Option[String] = None)
object SampleDocument {

  implicit val indexable = new Indexable[SampleDocument] {
    def version(document: SampleDocument): Int = document.version
    def id(document: SampleDocument): String = document.canonicalId
  }

  implicit val encoder: Encoder[SampleDocument] = deriveEncoder

  implicit val decoder: Decoder[SampleDocument] = deriveDecoder
}

trait ElasticIndexerFixtures extends ElasticsearchFixtures with Akka {
  this: Suite =>

  def withElasticIndexer[T, R](idx: Index,
                               esClient: ElasticClient = elasticClient)(
    testWith: TestWith[ElasticIndexer[T], R])(implicit
                                              ec: ExecutionContext,
                                              encoder: Encoder[T],
                                              indexable: Indexable[T]): R =
    testWith(new ElasticIndexer[T](esClient, idx))

  object NoStrictMapping extends IndexConfig {
    val analysis: Analysis = Analysis(analyzers = List())
    val mapping: MappingDefinition = MappingDefinition.empty
  }

  implicit def canonicalId[T](
    implicit indexable: Indexable[T]): CanonicalId[T] =
    (doc: T) => indexable.id(doc)
}
