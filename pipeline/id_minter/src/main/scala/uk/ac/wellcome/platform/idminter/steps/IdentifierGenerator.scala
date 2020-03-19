package uk.ac.wellcome.platform.idminter.steps

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.database.IdentifiersDao
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.platform.idminter.utils.Identifiable

import scala.collection.breakOut
import scala.util.{Success, Try}

class IdentifierGenerator(identifiersDao: IdentifiersDao) extends Logging {
  def retrieveOrGenerateCanonicalIds(sourceIdentifiers: Seq[SourceIdentifier])
    : Try[Map[SourceIdentifier, Identifier]] =
    identifiersDao
      .lookupIds(sourceIdentifiers)
      .flatMap {
        case IdentifiersDao.LookupResult(foundMap, notFoundIdentifiers) =>
          generateAndSaveCanonicalIds(notFoundIdentifiers).map {
            newIdentifiers =>
              val newMap: Map[SourceIdentifier, Identifier] =
                (notFoundIdentifiers zip newIdentifiers)(breakOut)
              foundMap ++ newMap
          }
      }

  private def generateAndSaveCanonicalIds(
    sourceIdentifiers: List[SourceIdentifier]): Try[List[Identifier]] =
    sourceIdentifiers match {
      case Nil => Success(Nil)
      case _ =>
        identifiersDao
          .saveIdentifiers(
            sourceIdentifiers.map { id =>
              Identifier(
                canonicalId = Identifiable.generate,
                sourceIdentifier = id
              )
            }
          )
          .map(_.succeeded)
    }

}
