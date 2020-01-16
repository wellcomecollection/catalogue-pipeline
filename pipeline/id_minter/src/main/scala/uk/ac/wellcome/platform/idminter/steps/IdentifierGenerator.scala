package uk.ac.wellcome.platform.idminter.steps

import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.platform.idminter.services.IdentifiersService
import uk.ac.wellcome.platform.idminter.utils.Identifiable

import scala.util.Try

class IdentifierGenerator(identifiersDao: IdentifiersService) {

  def retrieveOrGenerateCanonicalId(
    identifier: SourceIdentifier
  ): Try[String] =
    Try {
      identifiersDao
        .lookupId(
          sourceIdentifier = identifier
        )
        .flatMap {
          case Some(id) => Try(id.id)
          case None     => generateAndSaveCanonicalId(identifier)
        }
    }.flatten

  private def generateAndSaveCanonicalId(
    sourceIdentifier: SourceIdentifier
  ): Try[String] = {
    val canonicalId = Identifiable.generate
    identifiersDao
      .saveIdentifier(
        sourceIdentifier,
        Identifier(
          id = canonicalId,
          sourceIdentifier = sourceIdentifier
        )
      )
      .map { _ =>
        canonicalId
      }
  }
}
