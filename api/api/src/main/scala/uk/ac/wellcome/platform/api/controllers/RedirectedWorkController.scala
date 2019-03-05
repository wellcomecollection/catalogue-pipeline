package uk.ac.wellcome.platform.api.controllers

import com.twitter.finagle.http.Request
import uk.ac.wellcome.models.work.internal.IdentifiedRedirectedWork
import uk.ac.wellcome.platform.api.models.ApiConfig

trait RedirectedWorkController {

  /** Create a 302 Redirect to a new Work.
    *
    * Assumes the original URI requested was for a single work, i.e. a request
    * of the form /works/{id}.
    *
    */
  def createRedirectResponseURI(originalRequest: Request,
                                work: IdentifiedRedirectedWork,
                                apiConfig: ApiConfig): String = {
    val path = originalRequest.path.replaceAll(
      s"/${work.canonicalId}",
      s"/${work.redirect.canonicalId}"
    )

    val params = originalRequest.params
      .filterNot { case (k, _) => k == "id" }
      .map { case (k, v) => s"$k=$v" }

    val appendToPath = if (params.nonEmpty) {
      val paramString =
        params.reduce((a: String, b: String) => s"$a&$b")
      s"?$paramString"
    } else {
      ""
    }

    s"$path$appendToPath"
  }
}
