package uk.ac.wellcome.platform.api.works.v1

import com.twitter.finatra.http.EmbeddedHttpServer
import uk.ac.wellcome.platform.api.works.ApiErrorsTestBase
import uk.ac.wellcome.fixtures.TestWith

class ApiV1ErrorsTest extends ApiV1WorksTestBase with ApiErrorsTestBase {
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
}
