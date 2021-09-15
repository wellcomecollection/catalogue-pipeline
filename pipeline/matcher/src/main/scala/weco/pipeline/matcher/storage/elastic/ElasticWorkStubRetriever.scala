package weco.pipeline.matcher.storage.elastic

import com.sksamuel.elastic4s.ElasticDsl.get
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.requests.get.{GetRequest, GetResponse}
import com.sksamuel.elastic4s.{ElasticClient, Index}
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.Implicits._
import weco.pipeline.matcher.models.WorkStub
import weco.pipeline_storage.elastic.ElasticRetriever

import scala.concurrent.ExecutionContext
import scala.util.Try

/** The matcher only needs to know about linking data on works; most of the fields
  * it can completely ignore.
  *
  * This custom retriever fetches only the specific fields that the matcher needs, to
  * reduce the amount of data we have to get out of Elasticsearch (and pay to send through
  * the AWS NAT Gateway).
  */
class ElasticWorkStubRetriever(val client: ElasticClient, val index: Index)(
  implicit val ec: ExecutionContext
) extends ElasticRetriever[WorkStub] {

  override def createGetRequest(id: String): GetRequest =
    get(index, id)
      .fetchSourceInclude(
        "state.canonicalId",
        "state.sourceIdentifier.identifierType.id",
        "state.sourceIdentifier.ontologyType",
        "state.sourceIdentifier.value",
        "state.sourceModifiedTime",
        "data.mergeCandidates",
        "version",
        "type",
        // Although we don't use these two fields directly, they're among
        // the minimum you need to serialise an instance of Work.Deleted.
        "deletedReason.info",
        "deletedReason.type",
      )

  override def parseGetResponse(response: GetResponse): Try[WorkStub] =
    response.safeTo[Work[Identified]].map { work =>
      val id = work.state.canonicalId
      val referencedWorkIds = work.data.mergeCandidates
        .map { mergeCandidate =>
          mergeCandidate.id.canonicalId
        }
        .filterNot { _ == id }
        .toSet

      WorkStub(
        id = id,
        modifiedTime = work.state.modifiedTime,
        referencedWorkIds = referencedWorkIds
      )
    }
}
