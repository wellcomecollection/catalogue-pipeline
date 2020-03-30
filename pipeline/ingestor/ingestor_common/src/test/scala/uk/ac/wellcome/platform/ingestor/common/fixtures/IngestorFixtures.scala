package uk.ac.wellcome.platform.ingestor.common.fixtures

import com.sksamuel.elastic4s.requests.analysis.Analysis
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import com.sksamuel.elastic4s.{ElasticClient, Index, Indexable}
import io.circe.Decoder
import org.scalatest.Suite
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.memory.MemoryTypedStoreCompanion
import uk.ac.wellcome.elasticsearch.model.{CanonicalId, Version}
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.elasticsearch.{ElasticsearchIndexCreator, IndexConfig}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil.toJson
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.platform.ingestor.common.Indexer
import uk.ac.wellcome.platform.ingestor.common.models.IngestorConfig
import uk.ac.wellcome.platform.ingestor.common.services.IngestorWorkerService
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.streaming.Codec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import uk.ac.wellcome.json.JsonUtil._

case class SampleDocument(version: Int, canonicalId: String, title: String, data: SampleDocumentData = SampleDocumentData())
case class SampleDocumentData(stuff: Option[String] = None)
object SampleDocument {
  implicit val canonicalId: CanonicalId[SampleDocument] = (t: SampleDocument) => t.canonicalId
  implicit val indexable: Indexable[SampleDocument] = (t: SampleDocument) => toJson(t).get
  implicit val version: Version[SampleDocument] = (t: SampleDocument) => t.version
}

trait IngestorFixtures
    extends ElasticsearchFixtures
    with BigMessagingFixture {
  this: Suite =>


  def withIndexer[T,R](i: Index, esClient: ElasticClient = elasticClient)(testWith: TestWith[Indexer[T], R])(implicit e: ExecutionContext, idx: Indexable[T], canonicalId: CanonicalId[T], v: Version[T]) = {

      val indexer = new Indexer[T]{
        override val client: ElasticClient = esClient
        override implicit val ec: ExecutionContext = e
        override implicit val indexable: Indexable[T] = idx
        override implicit val id: CanonicalId[T] = canonicalId
        override implicit val version: Version[T] = v
        override val indexName: Index = i
      }
      testWith(indexer)
  }

  def withWorkerService[T,R](queue: Queue,
                           index: Index,
                             indexConfig: IndexConfig,
                             indexer: Indexer[T],
                           elasticClient: ElasticClient = elasticClient)(
    testWith: TestWith[IngestorWorkerService[T], R])(implicit dec: Decoder[T], codec: Codec[T]): R =
    withActorSystem { implicit actorSystem =>
      {
        implicit val typedStoreT =
          MemoryTypedStoreCompanion[ObjectLocation, T]()
        withBigMessageStream[T, R](queue) { messageStream =>
          val ingestorConfig = IngestorConfig(
            batchSize = 100,
            flushInterval = 5 seconds
          )

          val workerService = new IngestorWorkerService(
            indexCreator = new ElasticsearchIndexCreator(elasticClient, index, indexConfig),
            documentIndexer = indexer,
            ingestorConfig = ingestorConfig,
            messageStream = messageStream
          )

          workerService.run()

          testWith(workerService)
        }
      }
    }

  object NoStrictMapping extends IndexConfig {

    val analysis = Analysis(
      analyzers = List())
    val mapping = MappingDefinition.empty
  }
}
