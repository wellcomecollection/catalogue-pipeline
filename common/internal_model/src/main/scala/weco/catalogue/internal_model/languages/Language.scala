package weco.catalogue.internal_model.languages

import grizzled.slf4j.Logging

/** Represents the language of a work.
  *
  * Note: unlike other models that have an id/label field, here there isn't
  * a 1:1 mapping between the two.  In particular, it's possible for two instances
  * of Language to have the same ID but different labels.
  *
  * e.g. you might have two languages:
  *
  *       Language(it = "ita", label = "Italian")
  *       Language(it = "ita", label = "Judeo-Italian")
  *
  * This is based on the MARC language code list.  A single language might have
  * multiple variants that we want to count together for filtering/aggregations,
  * but we also want to capture the distinction on individual works pages.
  *
  */
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
