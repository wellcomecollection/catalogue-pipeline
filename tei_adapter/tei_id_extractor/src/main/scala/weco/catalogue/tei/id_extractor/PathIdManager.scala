package weco.catalogue.tei.id_extractor

import scalikejdbc.TxBoundary.Try._
import scalikejdbc._
import weco.catalogue.source_model.Implicits._
import weco.messaging.MessageSender
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.Writable
import weco.catalogue.source_model.tei.{
  TeiIdChangeMessage,
  TeiIdDeletedMessage,
  TeiIdMessage
}
import weco.catalogue.tei.id_extractor.PathIdManager._
import weco.catalogue.tei.id_extractor.database.PathIdTable
import weco.catalogue.tei.id_extractor.models.PathId

import java.time.Instant
import scala.util.{Failure, Success, Try}

class PathIdManager[Dest](
  pathIdTable: PathIdTable,
  store: Writable[S3ObjectLocation, String],
  messageSender: MessageSender[Dest],
  bucket: String
) {

  def handlePathChanged(pathId: PathId, blobContent: String): Try[Unit] =
    DB localTx {
      implicit session =>
        for {
          idRow <- selectById(pathIdTable, pathId.id)
          pathRow <- selectByPath(pathIdTable, pathId.path)
          _ <- (idRow, pathRow) match {
            case (Some(row), None) =>
              update(pathId, blobContent, row)
            case (None, None) =>
              insert(pathId, blobContent)
            case (None, Some(row)) =>
              updatePathRowDeleteOldId(pathId, blobContent, row)
            case (Some(idRow), Some(pathRow)) if idRow == pathRow =>
              update(pathId, blobContent, idRow)
            case (Some(idRow), Some(pathRow)) =>
              deletePathRowUpdateIdRow(pathId, blobContent, idRow, pathRow)
          }
        } yield ()
    }

  def handlePathDeleted(path: String, timeDeleted: Instant): Try[Unit] =
    DB localTx {
      implicit session =>
        for {
          maybePathId <- selectByPath(pathIdTable, path)
          _ <- maybePathId match {
            case Some(existingPathId) =>
              delete(existingPathId, timeDeleted)
            case None => Success(())
          }
        } yield ()
    }

  // delete row matching path & update row matching id
  private def deletePathRowUpdateIdRow(
    pathId: PathId,
    blobContent: String,
    idRow: PathId,
    pathRow: PathId
  )(implicit session: DBSession): Try[Unit] =
    if (
      pathId.timeModified.isAfter(idRow.timeModified) && pathId.timeModified
        .isAfter(pathRow.timeModified)
    ) {
      for {
        _ <- storeAndSendChange(pathId, blobContent)
        _ <- sendDeleted(pathRow.copy(timeModified = pathId.timeModified))
        _ <- deletePathId(pathIdTable, pathId.path)
        _ <- updateById(pathIdTable, pathId)
      } yield ()
    } else Success(())

  // update row matching path with new id & send delete message for old id
  private def updatePathRowDeleteOldId(
    pathId: PathId,
    blobContent: String,
    storedPathId: PathId
  )(implicit session: DBSession): Try[Unit] =
    if (pathId.timeModified.isAfter(storedPathId.timeModified)) {
      for {
        _ <- storeAndSendChange(pathId, blobContent)
        _ <- sendDeleted(storedPathId.copy(timeModified = pathId.timeModified))
        _ <- updateByPath(pathIdTable, pathId)
      } yield ()
    } else Success(())

  // delete a pathId from the database
  private def delete(pathId: PathId, timeDeleted: Instant)(
    implicit session: DBSession
  ) =
    if (timeDeleted.isAfter(pathId.timeModified)) {
      for {
        _ <- sendDeleted(pathId.copy(timeModified = timeDeleted))
        _ <- deletePathId(pathIdTable, pathId.path)
      } yield ()
    } else Success(())

  // insert a new pathid in the database
  private def insert(pathId: PathId, blobContent: String)(
    implicit session: DBSession
  ) =
    for {
      _ <- storeAndSendChange(pathId, blobContent)
      - <- insertPathId(pathIdTable, pathId)
    } yield ()

  // update a path id
  private def update(pathId: PathId, blobContent: String, storedPathId: PathId)(
    implicit session: DBSession
  ) =
    if (pathId.timeModified.isAfter(storedPathId.timeModified)) {
      for {
        _ <- storeAndSendChange(pathId, blobContent)
        _ <- updateById(pathIdTable, pathId)
      } yield ()
    } else Success(())

  private def sendDeleted(pathId: PathId) =
    messageSender.sendT[TeiIdMessage](
      TeiIdDeletedMessage(pathId.id, pathId.timeModified)
    )

  private def storeAndSendChange(pathId: PathId, blobContent: String) =
    for {
      stored <- storeTei(pathId, blobContent)
      message = TeiIdChangeMessage(pathId.id, stored.id, pathId.timeModified)
      _ <- messageSender.sendT[TeiIdMessage](message)
    } yield ()

  private def storeTei(pathId: PathId, blobContent: String) = {
    val location = S3ObjectLocation(
      bucket,
      s"tei_files/${pathId.id}/${pathId.timeModified.getEpochSecond}.xml"
    )
    store.put(location)(blobContent) match {
      case Right(stored) => Success(stored)
      case Left(err) =>
        Failure(new RuntimeException(s"Error putting $location: ", err.e))
    }
  }
}

object PathIdManager {

  def selectById(pathIdTable: PathIdTable, id: String)(
    implicit session: DBSession
  ) =
    Try(withSQL {
      select
        .from(pathIdTable as pathIdTable.p)
        .where
        .eq(pathIdTable.p.id, id)
        .forUpdate
    }.map(models.PathId(pathIdTable.p)).single().apply())

  def selectByPath(pathIdTable: PathIdTable, path: String)(
    implicit session: DBSession
  ) =
    Try(withSQL {
      select
        .from(pathIdTable as pathIdTable.p)
        .where
        .eq(pathIdTable.p.path, path)
        .forUpdate
    }.map(models.PathId(pathIdTable.p)).single().apply())

  def updateById(pathIdTable: PathIdTable, pathId: PathId)(
    implicit session: DBSession
  ) =
    Try(withSQL {
      update(pathIdTable)
        .set(
          pathIdTable.column.path -> pathId.path,
          pathIdTable.column.timeModified -> pathId.timeModified.toEpochMilli
        )
        .where
        .eq(pathIdTable.column.id, pathId.id)
    }.update.apply())

  def updateByPath(pathIdTable: PathIdTable, pathId: PathId)(
    implicit session: DBSession
  ) =
    Try(withSQL {
      update(pathIdTable)
        .set(
          pathIdTable.column.id -> pathId.id,
          pathIdTable.column.timeModified -> pathId.timeModified.toEpochMilli
        )
        .where
        .eq(pathIdTable.column.path, pathId.path)
    }.update.apply())

  def insertPathId(pathIdTable: PathIdTable, pathId: PathId)(
    implicit session: DBSession
  ) =
    Try(withSQL {
      insert
        .into(pathIdTable)
        .namedValues(
          pathIdTable.column.path -> pathId.path,
          pathIdTable.column.id -> pathId.id,
          pathIdTable.column.timeModified -> pathId.timeModified.toEpochMilli
        )
    }.update.apply())

  def deletePathId(pathIdTable: PathIdTable, path: String)(
    implicit session: DBSession
  ) =
    Try(withSQL {
      delete.from(pathIdTable).where.eq(pathIdTable.column.path, path)
    }.update().apply())
}
