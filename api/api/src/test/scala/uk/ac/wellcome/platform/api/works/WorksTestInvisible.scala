package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.models.Implicits._
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Indexed

class WorksTestInvisible extends ApiWorksTestBase {
  val invisibleWork: Work.Invisible[Indexed] =
    indexedWork().title("This work is invisible").invisible()

  it("returns an HTTP 410 Gone if looking up a work with visible = false") {
    withWorksApi {
      case (worksIndex, routes) =>
        insertIntoElasticsearch(worksIndex, invisibleWork)
        val path = s"/$apiPrefix/works/${invisibleWork.state.canonicalId}"
        assertJsonResponse(routes, path) {
          Status.Gone -> deleted(apiPrefix)
        }
    }
  }

  it("excludes works with visible=false from list results") {
    withWorksApi {
      case (worksIndex, routes) =>
        val works = indexedWorks(count = 2).sortBy {
          _.state.canonicalId
        }

        val worksToIndex = Seq[Work[Indexed]](invisibleWork) ++ works
        insertIntoElasticsearch(worksIndex, worksToIndex: _*)

        assertJsonResponse(routes, s"/$apiPrefix/works") {
          Status.OK -> worksListResponse(apiPrefix, works = works)
        }
    }
  }

  it("excludes works with visible=false from search results") {
    withWorksApi {
      case (worksIndex, routes) =>
        val work = indexedWork().title("This shouldn't be invisible!")
        insertIntoElasticsearch(worksIndex, work, invisibleWork)

        assertJsonResponse(routes, s"/$apiPrefix/works?query=invisible") {
          Status.OK -> worksListResponse(apiPrefix, works = Seq(work))
        }
    }
  }
}
