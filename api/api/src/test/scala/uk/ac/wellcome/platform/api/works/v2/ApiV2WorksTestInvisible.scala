package uk.ac.wellcome.platform.api.works.v2

import uk.ac.wellcome.models.work.internal.IdentifiedBaseWork

class ApiV2WorksTestInvisible extends ApiV2WorksTestBase {

  val deletedWork = createIdentifiedInvisibleWork

  it("returns an HTTP 410 Gone if looking up a work with visible = false") {
    withApi {
      case (indexV2, routes) =>
        insertIntoElasticsearch(indexV2, deletedWork)
        val path = s"/$apiPrefix/works/${deletedWork.canonicalId}"
        assertJsonResponse(routes, path) {
          Status.Gone -> deleted(apiPrefix)
        }
    }
  }

  it("excludes works with visible=false from list results") {
    withApi {
      case (indexV2, routes) =>
        val works = createIdentifiedWorks(count = 2).sortBy { _.canonicalId }

        val worksToIndex = Seq[IdentifiedBaseWork](deletedWork) ++ works
        insertIntoElasticsearch(indexV2, worksToIndex: _*)

        assertJsonResponse(routes, s"/$apiPrefix/works") {
          Status.OK -> worksListResponse(apiPrefix, works = works)
        }
    }
  }

  it("excludes works with visible=false from search results") {
    withApi {
      case (indexV2, routes) =>
        val work = createIdentifiedWorkWith(
          title = Some("This shouldn't be deleted!")
        )
        insertIntoElasticsearch(indexV2, work, deletedWork)

        assertJsonResponse(routes, s"/$apiPrefix/works?query=deleted") {
          Status.OK -> worksListResponse(apiPrefix, works = Seq(work))
        }
    }
  }
}
