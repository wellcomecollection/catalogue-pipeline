package uk.ac.wellcome.platform.api.works

import com.twitter.finagle.http.Status
import com.twitter.finatra.http.EmbeddedHttpServer
import org.apache.commons.io.IOUtils
import uk.ac.wellcome.display.models.ApiVersions

class ApiContextTest extends ApiWorksTestBase {

  it("returns a context for v2") {
    withHttpServer { server: EmbeddedHttpServer =>
      server.httpGet(
        path = s"/${getApiPrefix(ApiVersions.v2)}/context.json",
        andExpect = Status.Ok,
        withJsonBody = IOUtils
          .toString(getClass.getResourceAsStream("/context.json"), "UTF-8")
      )
    }
  }
}
