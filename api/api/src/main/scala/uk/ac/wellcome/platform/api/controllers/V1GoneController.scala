package uk.ac.wellcome.platform.api.controllers

import com.google.inject.{Inject, Singleton}
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import uk.ac.wellcome.display.models.ApiVersions
import uk.ac.wellcome.platform.api.models.{
  ApiConfig,
  DisplayError,
  Error,
  ErrorVariant
}
import uk.ac.wellcome.platform.api.responses.ResultResponse
import uk.ac.wellcome.platform.api.ContextHelper.buildContextUri

@Singleton
class V1GoneController @Inject()(apiConfig: ApiConfig) extends Controller {

  prefix(apiConfig.pathPrefix) {
    setupContextEndpoint(ApiVersions.v1)
  }

  private def setupContextEndpoint(version: ApiVersions.Value): Unit = {
    get(s"/${version.toString}/:*") { _: Request =>
      response.gone.json(
        ResultResponse(
          context = buildContextUri(apiConfig, version),
          result = DisplayError(Error(
            ErrorVariant.http410,
            Some("This API is now decommissioned. Please use https://api.wellcomecollection.org/catalogue/v2/works.")))
        ))
    }
  }
}
