package uk.ac.wellcome.platform.api.works.v1

import com.twitter.finagle.http.{Response, Status}
import com.twitter.finatra.http.EmbeddedHttpServer
import uk.ac.wellcome.fixtures.TestWith

class ApiV1ErrorsTest extends ApiV1WorksTestBase {
  def withServer[R](testWith: TestWith[EmbeddedHttpServer, R]): R =
    withV1Api {
      case (_, server: EmbeddedHttpServer) =>
        testWith(server)
    }

  describe("only returns Gone for V1") {
    it("for valid requests") {
      assertIsGoneRequest(
        "/works"
      )
    }

    it("for invalid requests") {
      assertIsGoneRequest(
        "/works?pageSize=horses"
      )
    }
  }

  def assertIsGoneRequest(path: String): Response =
    withServer { server: EmbeddedHttpServer =>
      server.httpGet(
        path = s"/$apiPrefix$path",
        andExpect = Status.Gone,
        withJsonBody = goneRequest(
          apiPrefix = apiPrefix,
          description =
            "This API is now decommissioned. Please use https://api.wellcomecollection.org/catalogue/v2/works."
        )
      )
    }
}
