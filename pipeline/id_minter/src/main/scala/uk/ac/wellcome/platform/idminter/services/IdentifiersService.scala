package uk.ac.wellcome.platform.idminter.services

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.exceptions.IdMinterException
import uk.ac.wellcome.platform.idminter.models.Identifier
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.{Identified, NotFoundError}

import scala.util.{Failure, Success, Try}

class IdentifiersService[StoreType <: Store[SourceIdentifier, Identifier]](
  store: StoreType
) extends Logging {

  def lookupId(
    sourceIdentifier: SourceIdentifier
  ): Try[Option[Identifier]] = {
    store.get(sourceIdentifier) match {
      case Right(identifier) => Success(Some(identifier.identifiedT))
      case Left(_: NotFoundError) => Success(None)
      case Left(storageError) => Failure(IdMinterException(storageError.e))
    }
  }

  def saveIdentifier(
    sourceIdentifier: SourceIdentifier,
    identifier: Identifier
  ): Try[Identified[SourceIdentifier, Identifier]] =
    store.put(sourceIdentifier)(identifier) match {
      case Right(result) => Success(result)
      case Left(writeError) => Failure(IdMinterException(writeError.e))
    }
}
