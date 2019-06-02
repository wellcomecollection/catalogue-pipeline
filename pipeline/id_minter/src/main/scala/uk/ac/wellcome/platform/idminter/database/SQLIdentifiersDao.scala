package uk.ac.wellcome.platform.idminter.database

import java.sql.SQLIntegrityConstraintViolationException

import grizzled.slf4j.Logging
import scalikejdbc._
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.exceptions.IdMinterException
import uk.ac.wellcome.platform.idminter.models.{Identifier, IdentifiersTable}
import uk.ac.wellcome.storage.{DaoReadError, DaoWriteError, DoesNotExistError}

import scala.concurrent.blocking
import scala.util.{Failure, Success, Try}

class SQLIdentifiersDao(db: DB, identifiers: IdentifiersTable) extends Logging with IdentifiersDao {

  implicit val session = AutoSession(db.settingsProvider)

  override def get(sourceIdentifier: SourceIdentifier): GetResult = Try {
    val sourceSystem = sourceIdentifier.identifierType.id
    val sourceId = sourceIdentifier.value

    // TODO: handle gracefully, don't TryBackoff ad infinitum
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
  } match {
    case Success(None) => Left(DoesNotExistError(
      new Throwable(s"No entry stored for source identifier $sourceIdentifier")
    ))
    case Success(Some(identifier)) => Right(identifier)
    case Failure(err) => Left(DaoReadError(err))
  }

  override def put(identifier: Identifier): PutResult = {
    Try {
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
  } match {
    case Success(_) => Right(())
    case Failure(err) => Left(DaoWriteError(err))
  }
}
