package uk.ac.wellcome.platform.api.works

import uk.ac.wellcome.models.work.internal.IdState
import uk.ac.wellcome.models.Implicits._

class WorksRedirectsTest extends ApiWorksTestBase {

  val redirectedWork = indexedWork().redirected(
    IdState.Identified(
      canonicalId = createCanonicalId,
      sourceIdentifier = createSourceIdentifier
    )
  )
  val redirectId = redirectedWork.redirectTarget.canonicalId

  it("returns a TemporaryRedirect if looking up a redirected work") {
    withWorksApi {
      case (worksIndex, routes) =>
        insertIntoElasticsearch(worksIndex, redirectedWork)
        val path = s"/$apiPrefix/works/${redirectedWork.state.canonicalId}"
        assertRedirectResponse(routes, path) {
          Status.Found -> s"/$apiPrefix/works/$redirectId"
        }
    }
  }

  it("preserves query parameters on a 302 Redirect") {
    withWorksApi {
      case (worksIndex, routes) =>
        insertIntoElasticsearch(worksIndex, redirectedWork)
        val path =
          s"/$apiPrefix/works/${redirectedWork.state.canonicalId}?include=identifiers"
        assertRedirectResponse(routes, path) {
          Status.Found -> s"/$apiPrefix/works/$redirectId?include=identifiers"
        }
    }
  }
}
