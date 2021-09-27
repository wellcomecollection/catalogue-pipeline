package weco.pipeline.matcher.storage.elastic

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.requests.get.{GetRequest, GetResponse}
import com.sksamuel.elastic4s.{ElasticClient, Index}
import weco.json.JsonUtil._
import weco.pipeline.matcher.models.WorkStub
import weco.pipeline_storage.elastic.ElasticRetriever

import scala.concurrent.ExecutionContext
import scala.util.Try

/** The matcher only needs to know about linking data on works; most of the fields
  * it can completely ignore.
  *
  * This custom retriever fetches only the specific fields that the matcher needs, to
  * reduce the amount of data we have to get out of Elasticsearch (and pay to send through
  * the VPC endpoint).
  */
class ElasticWorkStubRetriever(val client: ElasticClient, val index: Index)(
  implicit val ec: ExecutionContext
) extends ElasticRetriever[WorkStub] {

  override def createGetRequest(id: String): GetRequest =
    get(index, id).fetchSourceInclude("state", "version")

  override def parseGetResponse(response: GetResponse): Try[WorkStub] =
    response.safeTo[WorkStub]
}
