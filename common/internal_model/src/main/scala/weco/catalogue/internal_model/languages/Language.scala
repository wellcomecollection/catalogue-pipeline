package weco.catalogue.internal_model.languages

import grizzled.slf4j.Logging

case class Language(id: String, label: String) extends Logging {

  // We use the three-digit MARC language codes throughout the pipeline.
  // This consistency allows us to aggregate/filter across sources.
  //
  // Right now all the transformers should be writing three-digit codes here --
  // if they're not, that's something we should investigate.  This assertion is
  // to draw our attention to places where that might be the case, and we can
  // remove it if we find a legitimate reason to use other IDs here.
  require(
    id.length == 3,
    s"Expected a three-digit MARC language code as the ID, got $id"
  )
}
