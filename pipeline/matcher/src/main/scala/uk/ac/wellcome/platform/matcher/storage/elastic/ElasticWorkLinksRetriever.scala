package uk.ac.wellcome.platform.matcher.storage.elastic

import com.sksamuel.elastic4s.{ElasticClient, Index}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal.{Work, WorkState}
import uk.ac.wellcome.pipeline_storage.{ElasticRetriever, Retriever, RetrieverMultiResult}
import uk.ac.wellcome.platform.matcher.models.WorkLinks

import scala.concurrent.{ExecutionContext, Future}

class ElasticWorkLinksRetriever(client: ElasticClient, index: Index)(
  implicit val ec: ExecutionContext
) extends Retriever[WorkLinks] {
  private val underlying = new ElasticRetriever[Work[WorkState.Identified]](client, index)

  override def apply(ids: Seq[String]): Future[RetrieverMultiResult[WorkLinks]] =
    underlying.apply(ids).map { result =>
      RetrieverMultiResult(
        found = result.found.map { case (id, work) => id -> WorkLinks(work) },
        notFound = result.notFound
      )
    }
}
