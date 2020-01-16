package uk.ac.wellcome.platform.idminter.models

import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.storage.Identifiable

case class Identifier(
                       id: String,
                       sourceIdentifier: SourceIdentifier
) extends Identifiable[String]