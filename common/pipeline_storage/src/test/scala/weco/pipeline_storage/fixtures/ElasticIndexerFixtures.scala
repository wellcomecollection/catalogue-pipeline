package weco.pipeline_storage.fixtures

import com.sksamuel.elastic4s.{ElasticClient, Index}
import io.circe.Encoder
import org.scalatest.Suite
import weco.catalogue.internal_model.fixtures.index.IndexFixtures
import weco.elasticsearch.model.IndexId
import weco.elasticsearch.IndexConfig
import weco.fixtures.TestWith
import weco.pipeline_storage.Indexable
import weco.pipeline_storage.elastic.ElasticIndexer

import scala.concurrent.{ExecutionContext, Future}

trait ElasticIndexerFixtures extends IndexFixtures {
  this: Suite =>
  def withElasticIndexer[T, R](idx: Index,
                               esClient: ElasticClient = elasticClient,
                               config: IndexConfig = IndexConfig.empty)(
    testWith: TestWith[ElasticIndexer[T], R])(implicit
                                              ec: ExecutionContext,
                                              encoder: Encoder[T],
                                              indexable: Indexable[T]): R =
    testWith(new ElasticIndexer[T](esClient, idx, config))

  implicit def canonicalId[T](implicit indexable: Indexable[T]): IndexId[T] =
    (doc: T) => indexable.id(doc)

  def indexInOrder[T](indexer: ElasticIndexer[T])(documents: T*)(
    implicit ec: ExecutionContext): Future[Either[Seq[T], Seq[T]]] =
    documents.tail.foldLeft(indexer(List(documents.head))) { (future, doc) =>
      future.flatMap { _ =>
        indexer(List(doc))
      }
    }
}
