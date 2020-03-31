package uk.ac.wellcome.platform.ingestor.works.services

import com.sksamuel.elastic4s.{ElasticClient, Index}
import uk.ac.wellcome.json.JsonUtil.toJson
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.{IdentifiedBaseWork, IdentifiedInvisibleWork, IdentifiedRedirectedWork, IdentifiedWork}
import uk.ac.wellcome.platform.ingestor.common.Indexer

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

class WorkIndexer(
 val  client: ElasticClient,
 val index: Index
)(implicit val ec: ExecutionContext)
    extends Indexer[IdentifiedBaseWork] {

  implicit val indexable = (t: IdentifiedBaseWork) => toJson(t).get

  implicit val id = (t: IdentifiedBaseWork) => t.canonicalId

  /**
    * When the merger makes the decision to merge some works, it modifies the content of
    * the affected works. Despite the content of these works being modified, their version
    * remains the same. Instead, the merger sets the merged flag to true for the target work
    * and changes the type of the other works to redirected.
    *
    * When we ingest those works, we need to make sure that the merger modified works
    * are never overridden by unmerged works for the same version still running through
    * the pipeline.
    * We also need to make sure that, if a work is modified by a source in such a way that
    * it shouldn't be merged (or redirected) anymore, the new unmerged version is ingested
    * and never replaced by the previous merged version still running through the pipeline.
    *
    * We can do that by ingesting works into Elasticsearch with a version derived by a
    * combination of work version, merged flag and work type. More specifically, by
    * multiplying the work version by 10, we make sure that a new version of a work
    * always wins over previous versions (merged or unmerged).
    * We make sure that a merger modified work always wins over other works with the same
    * version, by adding one to work.version * 10.
    */
    implicit val version = {
      case w: IdentifiedWork => (w.version * 10) + w.data.merged
      case w: IdentifiedRedirectedWork => (w.version * 10) + 1
      case w: IdentifiedInvisibleWork => w.version * 10
    }

  implicit private def toInteger(bool: Boolean): Int = if (bool) 1 else 0
}
