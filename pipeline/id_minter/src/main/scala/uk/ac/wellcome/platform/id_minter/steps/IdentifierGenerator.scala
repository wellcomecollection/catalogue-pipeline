package uk.ac.wellcome.platform.id_minter.steps

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.id_minter.database.IdentifiersDao
import uk.ac.wellcome.platform.id_minter.models.Identifier
import uk.ac.wellcome.platform.id_minter.utils.Identifiable

import scala.util.{Success, Try}

class IdentifierGenerator(identifiersDao: IdentifiersDao) extends Logging {
  import IdentifiersDao._
  /*
   * This function fetches canonicalIds for sourceIdentifiers, and generates
   * and saves canonicalIds where it can't find existing ones.
   *
   * Be aware that this means that if it is called in a multi-threaded
   * environment there may be inconsistency between the threads and some of
   * the updates will fail due to duplicate key errors, when an identifier
   * is saved by another thread after `lookupIds` has been called.
   */
  def retrieveOrGenerateCanonicalIds(sourceIdentifiers: Seq[SourceIdentifier])
    : Try[Map[SourceIdentifier, Identifier]] =
    identifiersDao
      .lookupIds(sourceIdentifiers)
      .flatMap {
        case LookupResult(existingIdentifiersMap, unmintedIdentifiers) =>
          generateAndSaveCanonicalIds(unmintedIdentifiers).map {
            newIdentifiers =>
              val newIdentifiersMap: Map[SourceIdentifier, Identifier] =
                (unmintedIdentifiers zip newIdentifiers).toMap
              existingIdentifiersMap ++ newIdentifiersMap
          }
      }

  private def generateAndSaveCanonicalIds(
    unmintedIdentifiers: List[SourceIdentifier]): Try[List[Identifier]] =
    unmintedIdentifiers match {
      case Nil => Success(Nil)
      case _ =>
        identifiersDao
          .saveIdentifiers(
            unmintedIdentifiers.map { id =>
              Identifier(
                canonicalId = Identifiable.generate,
                sourceIdentifier = id
              )
            }
          )
          .map(_.succeeded)
    }

}
