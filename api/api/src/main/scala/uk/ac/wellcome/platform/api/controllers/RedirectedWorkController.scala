package uk.ac.wellcome.platform.api.controllers

import java.net.URI

import uk.ac.wellcome.models.work.internal.IdentifiedRedirectedWork

trait RedirectedWorkController {

  /** Create a 302 Redirect to a new Work.
    *
    * Assumes the original URI requested was for a single work, i.e. a request
    * of the form /works/{id}.
    *
    */
  def createRedirectResponseURI(originalUri: String,
                                work: IdentifiedRedirectedWork,
                                apiScheme: String,
                                apiHost: String): URI = {
    val original = new URI(originalUri)

    new URI(
      apiScheme,
      apiHost,
      original.getPath.replaceAll(
        s"/works/${work.canonicalId}",
        s"/works/${work.redirect.canonicalId}"
      ),
      original.getQuery,
      original.getFragment
    )
  }
}
