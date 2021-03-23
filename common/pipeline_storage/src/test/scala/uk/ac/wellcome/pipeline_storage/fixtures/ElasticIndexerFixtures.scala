package uk.ac.wellcome.pipeline_storage.fixtures

import com.sksamuel.elastic4s.{ElasticClient, Index}
import io.circe.Encoder
import org.scalatest.Suite
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.elasticsearch.model.IndexId
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.elasticsearch.{IndexConfig, NoStrictMapping}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.pipeline_storage.{ElasticIndexer, Indexable}

import scala.concurrent.{ExecutionContext, Future}

trait ElasticIndexerFixtures extends ElasticsearchFixtures with Akka {
  this: Suite =>

  def withElasticIndexer[T, R](idx: Index,
                               esClient: ElasticClient = elasticClient,
                               config: IndexConfig = NoStrictMapping)(
    testWith: TestWith[ElasticIndexer[T], R])(implicit
                                              ec: ExecutionContext,
                                              encoder: Encoder[T],
                                              indexable: Indexable[T]): R =
    testWith(new ElasticIndexer[T](esClient, idx, config))

  implicit def canonicalId[T](implicit indexable: Indexable[T]): IndexId[T] =
    (doc: T) => indexable.id(doc)

  def ingestInOrder[T](indexer: ElasticIndexer[T])(documents: T*)(
    implicit ec: ExecutionContext): Future[Either[Seq[T], Seq[T]]] =
    documents.tail.foldLeft(indexer(List(documents.head))) { (future, doc) =>
      future.flatMap { _ =>
        indexer(List(doc))
      }
    }
}
