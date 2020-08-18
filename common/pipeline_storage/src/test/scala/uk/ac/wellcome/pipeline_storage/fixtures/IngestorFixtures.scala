package uk.ac.wellcome.pipeline_storage.fixtures

import com.sksamuel.elastic4s.requests.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import com.sksamuel.elastic4s.{ElasticClient, Index, Indexable}
import org.scalatest.Suite
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.elasticsearch.model.{CanonicalId, Version}
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.elasticsearch.IndexConfig
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil.toJson
import uk.ac.wellcome.pipeline_storage.Indexer

import scala.concurrent.ExecutionContext
import uk.ac.wellcome.json.JsonUtil._

case class SampleDocument(version: Int,
                          canonicalId: String,
                          title: String,
                          data: SampleDocumentData = SampleDocumentData())
case class SampleDocumentData(stuff: Option[String] = None)
object SampleDocument {
  implicit val canonicalId: CanonicalId[SampleDocument] = (t: SampleDocument) =>
    t.canonicalId
  implicit val indexable: Indexable[SampleDocument] = (t: SampleDocument) =>
    toJson(t).get
  implicit val version: Version[SampleDocument] = (t: SampleDocument) =>
    t.version
}

trait IngestorFixtures
    extends ElasticsearchFixtures
    with Akka {
  this: Suite =>

  def withIndexer[T, R](i: Index, esClient: ElasticClient = elasticClient)(
    testWith: TestWith[Indexer[T], R])(implicit e: ExecutionContext,
                                       idx: Indexable[T],
                                       canonicalId: CanonicalId[T],
                                       v: Version[T]): R = {

    val indexer = new Indexer[T] {
      override val client: ElasticClient = esClient
      override implicit val ec: ExecutionContext = e
      override implicit val indexable: Indexable[T] = idx
      override implicit val id: CanonicalId[T] = canonicalId
      override implicit val version: Version[T] = v
      override val index: Index = i
    }
    testWith(indexer)
  }

  object NoStrictMapping extends IndexConfig {
    val analysis: Analysis = Analysis(analyzers = List())
    val mapping: MappingDefinition = MappingDefinition.empty
  }
}
