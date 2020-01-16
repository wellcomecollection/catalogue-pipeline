package uk.ac.wellcome.platform.idminter.services

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.models.{Identifier, IdentifierDoesNotExistError}
import uk.ac.wellcome.platform.idminter.utils.DynamoIdentifierStore

import scala.util.{Failure, Success, Try}

class IdentifiersService(
  store: DynamoIdentifierStore
) extends Logging {

  def lookupId(
    sourceIdentifier: SourceIdentifier
  ): Try[Option[Identifier]] = {
    store.getBySourceIdentifier(sourceIdentifier) match {
      case Right(identifier) => Success(Some(identifier))
      case Left(_: IdentifierDoesNotExistError) => Success(None)
      case Left(err) => Failure(err)
    }
  }

  def saveIdentifier(
    sourceIdentifier: SourceIdentifier,
    identifier: Identifier
  ): Try[Identifier] =
    store.put(identifier) match {
      case Right(result) => Success(result)
      case Left(writeError) => Failure(writeError)
    }
}
