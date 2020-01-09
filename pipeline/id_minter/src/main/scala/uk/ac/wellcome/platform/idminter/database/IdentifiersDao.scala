package uk.ac.wellcome.platform.idminter.database

import java.sql.SQLIntegrityConstraintViolationException

import grizzled.slf4j.Logging
import scalikejdbc._
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.exceptions.IdMinterException
import uk.ac.wellcome.platform.idminter.models.{Identifier, IdentifiersTable}
import uk.ac.wellcome.storage.NotFoundError
import uk.ac.wellcome.storage.store.Store

import scala.concurrent.blocking
import scala.util.{Failure, Success, Try}

class IdentifiersDao(
                      db: DB,
                      identifiers: IdentifiersTable,
                      store: Store[SourceIdentifier, Identifier]
                    ) extends Logging {

  implicit val session = AutoSession(db.settingsProvider)

  private def sqlLookup(
                         sourceIdentifier: SourceIdentifier
                       ): Try[Option[Identifier]] = Try {

    val sourceSystem = sourceIdentifier.identifierType.id
    val sourceId = sourceIdentifier.value

    blocking {
      debug(s"Matching ($sourceIdentifier)")

      val i = identifiers.i
      val query = withSQL {
        select
          .from(identifiers as i)
          .where
          .eq(i.OntologyType, sourceIdentifier.ontologyType)
          .and
          .eq(i.SourceSystem, sourceSystem)
          .and
          .eq(i.SourceId, sourceId)

      }.map(Identifier(i)).single
      debug(s"Executing:'${query.statement}'")
      query.apply()
    }

  }

  private def sqlSave(identifier: Identifier): Try[Int] = Try {
      blocking {
        debug(s"Putting new identifier $identifier")
        withSQL {
          insert
            .into(identifiers)
            .namedValues(
              identifiers.column.CanonicalId -> identifier.CanonicalId,
              identifiers.column.OntologyType -> identifier.OntologyType,
              identifiers.column.SourceSystem -> identifier.SourceSystem,
              identifiers.column.SourceId -> identifier.SourceId
            )
        }.update().apply()
      }
    } recover {
      case e: SQLIntegrityConstraintViolationException =>
        warn(
          s"Unable to insert $identifier because of integrity constraints: ${e.getMessage}")
        throw IdMinterException(e)
      case e =>
        error(s"Failed inserting identifier $identifier in database", e)
        throw e
    }

  /* An unidentified record from the transformer can give us a list of
   * identifiers from the source systems, and an ontology type (e.g. "Work").n */
  def lookupId(
    sourceIdentifier: SourceIdentifier
  ): Try[Option[Identifier]] =

    store.get(sourceIdentifier) match {
      case Right(identifier) => Success(Some(identifier.identifiedT))
      case Left(_: NotFoundError) => sqlLookup(sourceIdentifier)
      case Left(storageError) => Failure(storageError.e)
    }

  /* Save an identifier into the database. */
  def saveIdentifier(
                      sourceIdentifier: SourceIdentifier,
                      identifier: Identifier
                    ): Try[Any] =

    store.put(sourceIdentifier)(identifier) match {
      case Right(_) => sqlSave(identifier)
      case Left(writeError) => Failure(writeError.e)
    }
}
