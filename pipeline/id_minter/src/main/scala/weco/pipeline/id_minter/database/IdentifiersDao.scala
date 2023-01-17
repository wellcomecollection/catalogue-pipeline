package weco.pipeline.id_minter.database

import grizzled.slf4j.Logging
import scalikejdbc._
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.pipeline.id_minter.models.{Identifier, IdentifiersTable}

import java.sql.{BatchUpdateException, Statement}
import scala.concurrent.blocking
import scala.concurrent.duration._
import scala.util.{Failure, Try}

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

  @throws(classOf[RuntimeException])
  def lookupIds(sourceIdentifiers: Seq[SourceIdentifier])(
    implicit session: DBSession = readOnlySession): Try[LookupResult] =
    Try {
      assert(
        sourceIdentifiers.nonEmpty,
        message = "Cannot look up an empty list of sourceIdentifiers"
      )
      debug(s"Looking up ($sourceIdentifiers)")
      val distinctIdentifiers = sourceIdentifiers.distinct

      val foundIdentifiers =
        withTimeWarning(threshold = 10 seconds, distinctIdentifiers) {
          batchSourceIdentifiers(distinctIdentifiers)
            .flatMap { identifierBatch =>
              blocking {
                val i = identifiers.i
                val matchRow = rowMatcherFromSyntaxProvider(i) _

                // The query is manually constructed like this because using WHERE IN
                // statements with multiple items causes a full-index scan on MySQL 5.6.
                // See: https://stackoverflow.com/a/53180813
                // Therefore, we perform the rewrites here on the client.
                //
                // Because of issues in `scalikejdbc`'s typing, it's necessary to
                // specify the `Nothing` type parameters as done here in order for
                // the `map` statement to work properly.
                val query = withSQL[Nothing] {
                  select
                    .from[Nothing](identifiers as i)
                    .where
                    .map { query =>
                      identifierBatch.tail.foldLeft(
                        query.withRoundBracket(matchRow(identifierBatch.head))
                      ) { (q, sourceIdentifier) =>
                        q.or.withRoundBracket(matchRow(sourceIdentifier))
                      }
                    }
                }.map(rs => {
                    val sourceIdentifier = getSourceIdentifierFromResult(i, rs)

                    if (distinctIdentifiers.contains(sourceIdentifier)) {
                      (sourceIdentifier, Identifier(i)(rs))
                    } else {
                      // It might seem like this is impossible -- how could the query return a source
                      // identifier we didn't request?
                      //
                      // The reason is that the database query is case-insensitive.
                      //
                      // This can occur if there's an existing source identifier with the same value
                      // but a different case, e.g. b13026252 and B13026252.  This occurs occasionally
                      // with METS works, where the work originally had an uppercase B but has now been
                      // fixed to use a lowercase b.
                      //
                      // Because the query is case insensitive, querying for "METS/b13026252" would return
                      // "METS/B13026252", which isn't what we were looking for.
                      //
                      // Because the METS case is fairly rare, we usually fix this by modifying the row in the
                      // ID minter database to correct the case of the source identifier.  To help somebody
                      // realise what's happened, we include a specific log for this case.
                      warn(msg =
                        s"identifier returned from db not found in request, trying case-insensitive/ascii normalized match: $sourceIdentifier")

                      throw SurplusIdentifierException(
                        sourceIdentifier,
                        distinctIdentifiers)
                    }
                  })
                  .list
                debug(s"Executing:'${query.statement}'")
                query.apply()
              }
            }
        }
      val existingIdentifiers = foundIdentifiers.toMap
      val otherIdentifiers = distinctIdentifiers.toSet -- existingIdentifiers.keySet
      LookupResult(
        existingIdentifiers = existingIdentifiers,
        unmintedIdentifiers = otherIdentifiers.toList
      )
    }

  @throws(classOf[InsertError])
  def saveIdentifiers(ids: List[Identifier])(
    implicit session: DBSession = NamedAutoSession('primary)
  ): Try[InsertResult] =
    Try {
      val values = ids.map(i =>
        Seq(i.CanonicalId.toString, i.OntologyType, i.SourceSystem, i.SourceId))
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
    } recoverWith {
      case e: BatchUpdateException =>
        val insertError = getInsertErrorFromException(e, ids)
        val failedIds = insertError.failed.map(_.SourceId)
        val succeededIds = insertError.succeeded.map(_.SourceId)
        error(
          s"Batch update failed for [$failedIds], succeeded for [$succeededIds]",
          e)
        Failure(insertError)
      case e =>
        error(s"Failed inserting IDs: [${ids.map(_.SourceId)}]")
        Failure(e)
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

  private def rowMatcherFromSyntaxProvider(i: SyntaxProvider[Identifier])(
    sourceIdentifier: SourceIdentifier)(query: ConditionSQLBuilder[_]) =
    query
      .eq(i.OntologyType, sourceIdentifier.ontologyType)
      .and
      .eq(i.SourceSystem, sourceIdentifier.identifierType.id)
      .and
      .eq(i.SourceId, sourceIdentifier.value)

  // Note: this could throw if the "SourceSystem" variable contains an identifierType
  // ID that we don't use, but if it does then something has gone very wrong.
  // That should be impossible in practice.
  private def getSourceIdentifierFromResult(
    i: SyntaxProvider[Identifier],
    rs: WrappedResultSet
  ): SourceIdentifier =
    SourceIdentifier(
      identifierType =
        IdentifierType.apply(rs.string(i.resultName.SourceSystem)),
      ontologyType = rs.string(i.resultName.OntologyType),
      value = rs.string(i.resultName.SourceId)
    )

  private val poolNames = Iterator
    .continually(List('replica, 'primary))
    .flatten

  // Round-robin between the replica and the primary for SELECTs.
  // If we use a single endpoint, we see the ID minter get slow, especially
  // for works with a large number of IDs.
  private def readOnlySession: ReadOnlyNamedAutoSession = {

    // The old version of this code was
    //
    //    val name = poolNames.next()
    //
    // We would sometimes see a NoSuchElementException thrown, even though
    // this should be an infinite iterator.
    //
    // Our guess is that the iterator isn't thread-safe, so we wrap it in
    // synchronized() to make it so.  See https://stackoverflow.com/q/30639945/1558022
    //
    // In case this still doesn't fix the error, we catch it, log a warning
    // and then choose the primary.  It means we can rule this out as a
    // source of problems, and reduce noise in the ID minter logs.
    //
    // If we don't see the warning, we can come back and remove the try block.
    //
    // For more discussion, see
    // https://github.com/wellcomecollection/platform/issues/4957
    // https://github.com/wellcomecollection/platform/issues/4851
    val name = try {
      synchronized(poolNames.next())
    } catch {
      case exc: NoSuchElementException =>
        warn(s"Unexpected NoSuchElementException when picking session: $exc")
        'primary
    }

    ReadOnlyNamedAutoSession(name)
  }

  // In MySQL 5.6, the optimiser isn't able to optimise huge index range scans,
  // so we batch the sourceIdentifiers ourselves to prevent these scans.
  //
  // Remember that there is a composite index, (ontologyType, sourceSystem, sourceId)
  // Call the first 2 keys of this the "ID type". A huge range scan happens when
  // the following conditions are satisfied:
  //
  // - there are multiple ID types present in the source identifiers
  // - there are more than one source IDs for any of these ID types
  //
  // This batching is not necessarily optimal but using it is many, many orders
  // of magnitude faster than not using it.
  private def batchSourceIdentifiers(
    sourceIdentifiers: Seq[SourceIdentifier]): Seq[Seq[SourceIdentifier]] = {
    val idTypeGroups =
      sourceIdentifiers
        .groupBy(i => (i.ontologyType, i.identifierType.id))
        .values
    if (idTypeGroups.size > 1 && idTypeGroups.exists(_.size > 1)) {
      idTypeGroups.toSeq
    } else {
      Seq(sourceIdentifiers)
    }
  }

  private def withTimeWarning[R](
    threshold: Duration,
    identifiers: Seq[SourceIdentifier])(f: => R): R = {
    val start = System.currentTimeMillis()
    val result = f
    val end = System.currentTimeMillis()

    val duration = end - start
    if (duration > threshold.toMillis) {
      warn(
        s"Query for ${identifiers.size} identifiers (first: ${identifiers.head.value}) took $duration milliseconds",
      )
    }

    result
  }
}
