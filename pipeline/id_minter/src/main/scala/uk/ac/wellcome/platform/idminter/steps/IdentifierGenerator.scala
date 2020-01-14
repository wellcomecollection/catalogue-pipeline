package uk.ac.wellcome.platform.idminter.steps

import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.database.IdentifiersDao
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.platform.idminter.utils.Identifiable
import uk.ac.wellcome.storage.store.Store

import scala.util.Try

class IdentifierGenerator[StoreType <: Store[SourceIdentifier, Identifier]](
                           identifiersDao: IdentifiersDao[StoreType]
                         ) {

  def retrieveOrGenerateCanonicalId(
    identifier: SourceIdentifier
  ): Try[String] =
    Try {
      identifiersDao
        .lookupId(
          sourceIdentifier = identifier
        )
        .flatMap {
          case Some(id) => Try(id.CanonicalId)
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
          canonicalId = canonicalId,
          sourceIdentifier = sourceIdentifier
        )
      )
      .map { _ =>
        canonicalId
      }
  }
}
