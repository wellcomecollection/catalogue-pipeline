package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import WorkState.Identified

class WorksTestInvisible extends ApiWorksTestBase {
  val deletedWork: Work.Invisible[Identified] = identifiedWork().invisible()

  it("returns an HTTP 410 Gone if looking up a work with visible = false") {
    withWorksApi {
      case (worksIndex, routes) =>
        insertIntoElasticsearch(worksIndex, deletedWork)
        val path = s"/$apiPrefix/works/${deletedWork.state.canonicalId}"
        assertJsonResponse(routes, path) {
          Status.Gone -> deleted(apiPrefix)
        }
    }
  }

  it("excludes works with visible=false from list results") {
    withWorksApi {
      case (worksIndex, routes) =>
        val works = identifiedWorks(count = 2).sortBy {
          _.state.canonicalId
        }

        val worksToIndex = Seq[Work[Identified]](deletedWork) ++ works
        insertIntoElasticsearch(worksIndex, worksToIndex: _*)

        assertJsonResponse(routes, s"/$apiPrefix/works") {
          Status.OK -> worksListResponse(apiPrefix, works = works)
        }
    }
  }

  it("excludes works with visible=false from search results") {
    withWorksApi {
      case (worksIndex, routes) =>
        val work = identifiedWork().title("This shouldn't be deleted!")
        insertIntoElasticsearch(worksIndex, work, deletedWork)

        assertJsonResponse(routes, s"/$apiPrefix/works?query=deleted") {
          Status.OK -> worksListResponse(apiPrefix, works = Seq(work))
        }
    }
  }
}
