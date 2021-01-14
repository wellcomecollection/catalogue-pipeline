package uk.ac.wellcome.platform.matcher.storage.elastic

import com.sksamuel.elastic4s.ElasticDsl.get
import com.sksamuel.elastic4s.requests.get.{GetRequest, GetResponse}
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.{ElasticClient, Index}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal.{IdState, MergeCandidate}
import uk.ac.wellcome.pipeline_storage.elastic.ElasticRetriever
import uk.ac.wellcome.platform.matcher.models.WorkLinks

import scala.concurrent.ExecutionContext
import scala.util.Try

/** The matcher only needs to know about linking data on works; most of the fields
 * it can completely ignore.
 *
 * This custom retriever fetches only the specific fields that the matcher needs, to
 * reduce the amount of data we have to get out of Elasticsearch (and pay to send through
 * the AWS NAT Gateway).
 */
class ElasticWorkLinksRetriever(val client: ElasticClient, val index: Index)(
  implicit val ec: ExecutionContext
) extends ElasticRetriever[WorkLinks] {

  override def createGetRequest(id: String): GetRequest =
    get(index, id)
      .fetchSourceInclude("state.canonicalId", "data.mergeCandidates", "version")

  override def parseGetResponse(response: GetResponse): Try[WorkLinks] =
    response.safeTo[WorkStub].map { stub =>
      val id = stub.state.canonicalId
      val referencedWorkIds = stub.data.mergeCandidates
        .map { mergeCandidate =>
          mergeCandidate.id.canonicalId
        }
        .filterNot { _ == id }
        .toSet

      WorkLinks(
        workId = id,
        version = stub.version,
        referencedWorkIds = referencedWorkIds
      )
    }

  private case class StateStub(canonicalId: String)
  private case class DataStub(mergeCandidates: List[MergeCandidate[IdState.Identified]])

  private case class WorkStub(
    state: StateStub,
    version: Int,
    data: DataStub)
}
