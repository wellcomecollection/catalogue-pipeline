package weco.catalogue.tei.id_extractor

import scalikejdbc.TxBoundary.Try._
import scalikejdbc._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Writable
import weco.catalogue.tei.id_extractor.PathIdManager._
import weco.catalogue.tei.id_extractor.database.PathIdTable
import weco.catalogue.tei.id_extractor.models.{PathId, TeiIdChangeMessage, TeiIdDeletedMessage, TeiIdMessage}

import java.time.Instant
import scala.util.{Failure, Success, Try}

class PathIdManager[Dest](pathIdTable: PathIdTable,
                          store: Writable[S3ObjectLocation, String],
                          messageSender: MessageSender[Dest],
                          bucket: String) {

  def handlePathChanged(pathId: PathId, blobContent: String): Try[Unit] = DB localTx { implicit session =>
      for {
        idRow <- selectById(pathIdTable, pathId.id)
        pathRow <- selectByPath(pathIdTable, pathId.path)
        _ <- (idRow, pathRow) match {
          case (Some(row), None) =>
            update(pathId, blobContent, row)
          case (None, None) =>
            insert(pathId, blobContent)
          case (None, Some(row)) =>
            deleteOldIdInsertNewId(pathId, blobContent, row)
          case (Some(idRow), Some(pathRow)) if idRow == pathRow=>
            update(pathId, blobContent, idRow)
          case (Some(idRow), Some(pathRow)) =>
              deleteOldIdUpdateNewId(pathId, blobContent, idRow, pathRow)
        }
      } yield ()
    }

  def handlePathDeleted(path: String, timeDeleted: Instant): Try[Unit] =
    DB localTx { implicit session =>
      for {
        maybePathId <- selectByPath(pathIdTable, path)
        _ <- maybePathId match {
          case Some(existingPathId) =>
            delete(existingPathId, timeDeleted)
          case None => Success(())
        }
      } yield ()
    }

  private def deleteOldIdUpdateNewId(
    pathId: PathId,
    blobContent: String,
    idRow: PathId,
    pathRow: PathId)(implicit session: DBSession): Try[Unit] =
    if (pathId.timeModified.isAfter(idRow.timeModified) && pathId.timeModified
          .isAfter(pathRow.timeModified)) {
      for {
        _ <- storeAndSendChange(pathId, blobContent)
        _ <- sendDeleted(pathRow.copy(timeModified = pathId.timeModified))
        _ <- deletePathId(pathIdTable, pathId.path)
        _ <- updatePathId(pathIdTable, pathId)
      } yield ()
    } else Success(())

  private def deleteOldIdInsertNewId(
    pathId: PathId,
    blobContent: String,
    storedPathId: PathId)(implicit session: DBSession): Try[Unit] =
    if (pathId.timeModified.isAfter(storedPathId.timeModified)) {
      for {
        _ <- storeAndSendChange(pathId, blobContent)
        _ <- sendDeleted(storedPathId.copy(timeModified = pathId.timeModified))
        _ <- deletePathId(pathIdTable, pathId.path)
        _ <- insertPathId(pathIdTable, pathId)
      } yield ()
    } else Success(())

  private def delete(pathId: PathId,
                     timeDeleted: Instant
                     )(implicit session: DBSession) =
    if (timeDeleted.isAfter(pathId.timeModified)) {
      for {
        _ <- sendDeleted(pathId.copy(timeModified = timeDeleted))
        _ <- deletePathId(pathIdTable, pathId.path)
      } yield ()
    } else Success(())

  private def insert(pathId: PathId, blobContent: String)(
    implicit session: DBSession) =
    for {
      _ <- storeAndSendChange(pathId, blobContent)
      - <- insertPathId(pathIdTable, pathId)
    } yield ()

  private def update(pathId: PathId, blobContent: String, storedPathId: PathId)(
    implicit session: DBSession) =
    if (pathId.timeModified.isAfter(storedPathId.timeModified)) {
      for {
        _ <- storeAndSendChange(pathId, blobContent)
        _ <- updatePathId(pathIdTable, pathId)
      } yield ()
    } else Success(())

  private def sendDeleted(pathId: PathId) =
    messageSender.sendT[TeiIdMessage](
      TeiIdDeletedMessage(pathId.id, pathId.timeModified))

  private def storeAndSendChange(pathId: PathId, blobContent: String) =
    for {
      stored <- storeTei(pathId, blobContent)
      message = TeiIdChangeMessage(pathId.id, stored.id, pathId.timeModified)
      _ <- messageSender.sendT[TeiIdMessage](message)
    } yield ()

  private def storeTei(pathId: PathId, blobContent: String) = {
    val location = S3ObjectLocation(
      bucket,
      s"tei_files/${pathId.id}/${pathId.timeModified.getEpochSecond}.xml")
    store.put(location)(blobContent) match {
      case Right(stored) => Success(stored)
      case Left(err) => Failure(new RuntimeException(s"Error putting $location: ", err.e))
    }
  }
}

object PathIdManager {

  def selectById(pathIds: PathIdTable, id: String)(
    implicit session: DBSession) =
    Try(withSQL {
      select.from(pathIds as pathIds.p).where.eq(pathIds.p.id, id).forUpdate
    }.map(models.PathId(pathIds.p)).single().apply())

  def selectByPath(pathIds: PathIdTable, path: String)(
    implicit session: DBSession) =
    Try(withSQL {
      select.from(pathIds as pathIds.p).where.eq(pathIds.p.path, path).forUpdate
    }.map(models.PathId(pathIds.p)).single().apply())

  def updatePathId(pathIds: PathIdTable, pathId: PathId)(
    implicit session: DBSession) =
    Try(withSQL {
      update(pathIds)
        .set(
          pathIds.column.path -> pathId.path,
          pathIds.column.timeModified -> pathId.timeModified.toEpochMilli
        )
        .where
        .eq(pathIds.column.id, pathId.id)
    }.update.apply())

  def insertPathId(pathIds: PathIdTable, pathId: PathId)(
    implicit session: DBSession) =
    Try(withSQL {
      insert
        .into(pathIds)
        .namedValues(
          pathIds.column.path -> pathId.path,
          pathIds.column.id -> pathId.id,
          pathIds.column.timeModified -> pathId.timeModified.toEpochMilli
        )
    }.update.apply())

  def deletePathId(pathIds: PathIdTable, path: String)(
    implicit session: DBSession) =
    Try(withSQL {
      delete.from(pathIds).where.eq(pathIds.column.path, path)
    }.update().apply())
}
