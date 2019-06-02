package uk.ac.wellcome.platform.idminter.steps

import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.database.IdentifiersDao
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.platform.idminter.utils.Identifiable
import uk.ac.wellcome.storage.{
  DaoError,
  DoesNotExistError,
  StorageError,
  WriteError
}

class IdentifierGenerator(identifiersDao: IdentifiersDao) {

  def retrieveOrGenerateCanonicalId(
    sourceIdentifier: SourceIdentifier
  ): Either[StorageError, String] =
    identifiersDao.get(sourceIdentifier) match {
      case Right(value) => Right(value.CanonicalId)
      case Left(_: DoesNotExistError) =>
        generateAndSaveCanonicalId(sourceIdentifier)
      case Left(err) => Left(err)
    }

  private def generateAndSaveCanonicalId(
    sourceIdentifier: SourceIdentifier
  ): Either[WriteError with DaoError, String] = {
    val canonicalId = Identifiable.generate
    identifiersDao
      .put(
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
