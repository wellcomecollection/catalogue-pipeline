package weco.pipeline_storage.elastic

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticClient, Index}
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.requests.get.{GetRequest, GetResponse}
import io.circe.Decoder

import scala.concurrent.ExecutionContext
import scala.util.Try

class ElasticSourceRetriever[T](val client: ElasticClient, val index: Index)(
  implicit val ec: ExecutionContext,
  decoder: Decoder[T]
) extends ElasticRetriever[T] {
  override def createGetRequest(id: String): GetRequest =
    get(index, id)

  override def parseGetResponse(response: GetResponse): Try[T] =
    response.safeTo[T]
}
