package weco.pipeline.relation_embedder.lib

import com.sksamuel.elastic4s.{ElasticClient, HitReader, RequestFailure, RequestSuccess}
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchRequest}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

// This object is copied from https://github.com/Philippus/elastic4s/blob/main/elastic4s-core/src/main/scala/com/sksamuel/elastic4s/requests/searches/SearchIterator.scala#L4
// It is modified to allow the scroll context to be closed when the iterator is exhausted
object SearchIterator {

  /**
   * Creates a new Iterator for instances of SearchHit by wrapping the given HTTP client.
   */
  def hits(client: ElasticClient, searchreq: SearchRequest)(implicit timeout: Duration): Iterator[SearchHit] =
    new Iterator[SearchHit] {
      require(searchreq.keepAlive.isDefined, "Search request must define keep alive value")

      import com.sksamuel.elastic4s.ElasticDsl._

      private var internalIterator: Iterator[SearchHit] = Iterator.empty
      private var scrollId: Option[String]      = None

      override def hasNext: Boolean = {
        val hasNext = internalIterator.hasNext || {
          internalIterator = fetchNext()
          internalIterator.hasNext
        }

        if (!hasNext) {
          closeScroll()
        }

        hasNext
      }

      override def next(): SearchHit = internalIterator.next()

      private def closeScroll(): Unit = {
        scrollId.foreach { id =>
          val f = client.execute(clearScroll(id))
          Await.result(f, timeout)
        }
      }

      def fetchNext(): Iterator[SearchHit] = {

        // we're either advancing a scroll id or issuing the first query w/ the keep alive set
        val f = scrollId match {
          case Some(id) => client.execute(searchScroll(id, searchreq.keepAlive.get))
          case None     => client.execute(searchreq)
        }

        val resp = Await.result(f, timeout)

        // in a search scroll we must always use the last returned scrollId
        val response = resp match {
          case RequestSuccess(_, _, _, result) => result
          case failure: RequestFailure         => sys.error(failure.toString)
        }

        scrollId = response.scrollId
        response.hits.hits.iterator
      }
    }

  /**
   * Creates a new Iterator for type T by wrapping the given HTTP client.
   * A typeclass HitReader[T] must be provided for marshalling of the search
   * responses into instances of type T.
   */
  def iterate[T](client: ElasticClient, searchreq: SearchRequest)(implicit
                                                                  reader: HitReader[T],
                                                                  timeout: Duration): Iterator[T] =
    hits(client, searchreq).map(_.to[T])
}
