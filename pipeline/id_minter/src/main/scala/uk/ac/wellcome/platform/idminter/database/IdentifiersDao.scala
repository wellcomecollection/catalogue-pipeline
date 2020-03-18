package uk.ac.wellcome.platform.idminter.database

import java.sql.{
  BatchUpdateException,
  SQLIntegrityConstraintViolationException,
  Statement
}

import grizzled.slf4j.Logging
import scalikejdbc._
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.exceptions.IdMinterException
import uk.ac.wellcome.platform.idminter.models.{Identifier, IdentifiersTable}

import scala.concurrent.blocking
import scala.util.Try

object IdentifiersDao {
  case class LookupResult(found: Map[SourceIdentifier, Identifier],
                          notFound: List[SourceIdentifier])
  case class InsertResult(succeeded: List[Identifier])

  case class InsertError(failed: List[Identifier],
                         e: Throwable,
                         succeeded: List[Identifier])
      extends Exception
}

class IdentifiersDao(db: DB, identifiers: IdentifiersTable) extends Logging {
  import IdentifiersDao._

  implicit val session = AutoSession(db.settingsProvider)

  def lookupIds(sourceIdentifiers: Seq[SourceIdentifier]): Try[LookupResult] = {
    Try {
      debug(s"Matching ($sourceIdentifiers)")
      val sqlParametersToSourceIdentifier =
        buildSqlQueryParameters(sourceIdentifiers)

      blocking {

        val i = identifiers.i
        val query = withSQL {

          select
            .from(identifiers as i)
            .where
            .in(
              (i.OntologyType, i.SourceSystem, i.SourceId),
              sqlParametersToSourceIdentifier.keys.toList)

        }.map(rs => {
            val foundParameter = buildSqlParametersFromResult(i, rs)
            sqlParametersToSourceIdentifier.get(foundParameter) match {
              case Some(sourceIdentifier) =>
                (sourceIdentifier, Identifier(i)(rs))
              case None =>
                // this should be impossible in practice
                throw new RuntimeException(
                  "The values returned by the query could not be matched to a sourceIdentifier")
            }

          })
          .list
        debug(s"Executing:'${query.statement}'")
        val foundIdentifiers = query.apply().toMap
        val notFoundIdentifiers =
          sqlParametersToSourceIdentifier.values.toSet -- foundIdentifiers.keySet
        LookupResult(
          foundIdentifiers,
          notFoundIdentifiers.toList
        )
      }
    }
  }

  @throws(classOf[InsertError])
  def saveIdentifiers(ids: List[Identifier]): Try[InsertResult] =
    Try {
      val values = ids.map(i =>
        Seq(i.CanonicalId, i.OntologyType, i.SourceSystem, i.SourceId))
      blocking {
        debug(s"Putting new identifier $ids")
        withSQL {
          insert
            .into(identifiers)
            .namedValues(
              identifiers.column.CanonicalId -> sqls.?,
              identifiers.column.OntologyType -> sqls.?,
              identifiers.column.SourceSystem -> sqls.?,
              identifiers.column.SourceId -> sqls.?
            )
        }.batch(values: _*).apply()
        InsertResult(ids)
      }
    } recover {
      case e: BatchUpdateException =>
        val insertError = getInsertErrorFromException(e, ids)
        val failedIds = insertError.failed.map(_.SourceId)
        val succeededIds = insertError.succeeded.map(_.SourceId)
        error(
          s"Batch update failed for [$failedIds], succeeded for [$succeededIds]",
          e)
        throw insertError
      case e =>
        error(s"Failed inserting IDs: [${ids.map(_.SourceId)}]")
        throw e
    }

  private def getInsertErrorFromException(
    exception: BatchUpdateException,
    ids: List[Identifier]): InsertError = {
    val groupedResult = ids.zip(exception.getUpdateCounts).groupBy {
      case (_, Statement.EXECUTE_FAILED) => Statement.EXECUTE_FAILED
      case (_, _)                        => Statement.SUCCESS_NO_INFO
    }
    val failedIdentifiers =
      groupedResult.getOrElse(Statement.EXECUTE_FAILED, Nil).map {
        case (identifier, _) => identifier
      }
    val succeededIdentifiers =
      groupedResult.getOrElse(Statement.SUCCESS_NO_INFO, Nil).map {
        case (identifier, _) => identifier
      }
    InsertError(failedIdentifiers, exception, succeededIdentifiers)
  }

  private def buildSqlQueryParameters(
    sourceIdentifiers: Seq[SourceIdentifier]) = {
    sourceIdentifiers.map { sourceIdentifier =>
      buildSqlParametersFromSourceIdentifier(sourceIdentifier) -> sourceIdentifier
    }.toMap
  }

  private def buildSqlParametersFromSourceIdentifier(
    sourceIdentifier: SourceIdentifier) = {
    (
      sourceIdentifier.ontologyType,
      sourceIdentifier.identifierType.id,
      sourceIdentifier.value)
  }

  private def buildSqlParametersFromResult(i: SyntaxProvider[Identifier],
                                           rs: WrappedResultSet) = {
    (
      rs.string(i.resultName.OntologyType),
      rs.string(i.resultName.SourceSystem),
      rs.string(i.resultName.SourceId))
  }

  /* An unidentified record from the transformer can give us a list of
   * identifiers from the source systems, and an ontology type (e.g. "Work").
   *
   * This method looks for existing IDs that have matching ontology type and
   * source identifiers.
   */
  def lookupId(
    sourceIdentifier: SourceIdentifier
  ): Try[Option[Identifier]] = Try {

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
  }

  /* Save an identifier into the database.
   *
   * Note that this will copy _all_ the fields on `Identifier`, nulling any
   * fields which aren't set on `Identifier`.
   */
  def saveIdentifier(identifier: Identifier): Try[Any] = {
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
  }
}
