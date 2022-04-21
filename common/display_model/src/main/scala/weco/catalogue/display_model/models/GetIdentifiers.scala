package weco.catalogue.display_model.models

import weco.catalogue.internal_model.identifiers.IdState

trait GetIdentifiers {
  protected def getIdentifiers(id: IdState, includesIdentifiers: Boolean) =
    if (includesIdentifiers)
      Option(id.allSourceIdentifiers.map(DisplayIdentifier(_)))
        .filter(_.nonEmpty)
    else
      None
}
