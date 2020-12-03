package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.WorkState.Indexed
import uk.ac.wellcome.models.work.internal._

class WorksTestDeleted extends ApiWorksTestBase {
  val deletedWork: Work.Deleted[Indexed] = indexedWork().deleted()

  it("returns an HTTP 410 Gone if looking up a deleted work") {
    withWorksApi {
      case (worksIndex, routes) =>
        insertIntoElasticsearch(worksIndex, deletedWork)
        val path = s"/$apiPrefix/works/${deletedWork.state.canonicalId}"
        assertJsonResponse(routes, path) {
          Status.Gone -> deleted(apiPrefix)
        }
    }
  }

  it("excludes deleted works from list results") {
    withWorksApi {
      case (worksIndex, routes) =>
        val works = indexedWorks(count = 2).sortBy {
          _.state.canonicalId
        }

        val worksToIndex = Seq[Work[Indexed]](deletedWork) ++ works
        insertIntoElasticsearch(worksIndex, worksToIndex: _*)

        assertJsonResponse(routes, s"/$apiPrefix/works") {
          Status.OK -> worksListResponse(apiPrefix, works = works)
        }
    }
  }

  it("excludes deleted works from search results") {
    withWorksApi {
      case (worksIndex, routes) =>
        val work = indexedWork().title("This shouldn't be deleted!")
        insertIntoElasticsearch(worksIndex, work, deletedWork)

        assertJsonResponse(routes, s"/$apiPrefix/works?query=deleted") {
          Status.OK -> worksListResponse(apiPrefix, works = Seq(work))
        }
    }
  }
}
