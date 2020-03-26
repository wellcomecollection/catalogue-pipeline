package uk.ac.wellcome.platform.idminter.database

import java.sql.{BatchUpdateException, Statement}

import grizzled.slf4j.Logging
import scalikejdbc._
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.models.{Identifier, IdentifiersTable}

import scala.concurrent.blocking
import scala.util.Try

object IdentifiersDao {
  case class LookupResult(
    existingIdentifiers: Map[SourceIdentifier, Identifier],
    unmintedIdentifiers: List[SourceIdentifier])
  case class InsertResult(succeeded: List[Identifier])

  case class InsertError(failed: List[Identifier],
                         e: Throwable,
                         succeeded: List[Identifier])
      extends Exception
}

class IdentifiersDao(identifiers: IdentifiersTable) extends Logging {
  import IdentifiersDao._

  implicit val session = AutoSession(db.settingsProvider)

  def lookupIds(sourceIdentifiers: Seq[SourceIdentifier]): Try[LookupResult] = {
    Try {
      debug(s"Matching ($sourceIdentifiers)")
      val identifierRows = sourceIdentifiers.map { id =>
        (id.ontologyType, id.identifierType.id, id.value)
      }
      val identifierRowsMap: Map[(String, String, String), SourceIdentifier] =
        (identifierRows zip sourceIdentifiers).toMap

      blocking {

        val i = identifiers.i
        val query = withSQL {

          select
            .from(identifiers as i)
            .where
            .in(
              (i.OntologyType, i.SourceSystem, i.SourceId),
              identifierRows
            )

        }.map(rs => {
            val row = getRowFromResult(i, rs)
            identifierRowsMap.get(row) match {
              case Some(sourceIdentifier) =>
                (sourceIdentifier, Identifier(i)(rs))
              case None =>
                // this should be impossible in practice
                throw new RuntimeException(
                  s"The row $row returned by the query could not be matched to a sourceIdentifier")
            }

          })
          .list
        debug(s"Executing:'${query.statement}'")
        val foundIdentifiers = query.apply().toMap
        val otherIdentifiers = sourceIdentifiers.toSet -- foundIdentifiers.keySet
        LookupResult(
          existingIdentifiers = foundIdentifiers,
          unmintedIdentifiers = otherIdentifiers.toList
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

  private def getRowFromResult(i: SyntaxProvider[Identifier],
                               rs: WrappedResultSet) = {
    (
      rs.string(i.resultName.OntologyType),
      rs.string(i.resultName.SourceSystem),
      rs.string(i.resultName.SourceId))
  }
}
