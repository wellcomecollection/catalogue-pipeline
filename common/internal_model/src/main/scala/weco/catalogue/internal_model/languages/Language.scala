package weco.catalogue.internal_model.languages

import grizzled.slf4j.Logging

case class Language(id: String, label: String) extends Logging {

  // We use the three-digit language codes in several of the transformers
  // (Sierra, Calm) and consistency allows us to aggregate/filter across sources.
  //
  // This isn't a hard requirement (yet), but a warning for us if we're putting
  // something obviously different in this field.
  if (id.length != 3) {
    warn(s"Expected a three-digit MARC language code as the ID, got $id. Is that correct?")
  }
}
