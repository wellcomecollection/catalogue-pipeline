package weco.pipeline.matcher.storage.elastic

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.get.GetRequest
import com.sksamuel.elastic4s.{ElasticClient, Index}
import weco.catalogue.internal_model.Implicits._
import weco.json.JsonUtil._
import weco.pipeline.matcher.models.WorkStub
import weco.pipeline_storage.elastic.ElasticSourceRetriever

import scala.concurrent.ExecutionContext

/** The matcher only needs to know about linking data on works; most of the
  * fields it can completely ignore.
  *
  * This custom retriever fetches only the specific fields that the matcher
  * needs, to reduce the amount of data we have to get out of Elasticsearch (and
  * pay to send through the VPC endpoint).
  */
class ElasticWorkStubRetriever(client: ElasticClient, index: Index)(implicit
  ec: ExecutionContext
) extends ElasticSourceRetriever[WorkStub](client, index) {

  override def createGetRequest(id: String): GetRequest =
    get(index, id).fetchSourceInclude("state", "version", "type")
}
