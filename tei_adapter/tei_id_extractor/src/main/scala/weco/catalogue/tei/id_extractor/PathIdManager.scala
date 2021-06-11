package weco.catalogue.tei.id_extractor

import scalikejdbc.TxBoundary.Try._
import scalikejdbc._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Writable
import weco.catalogue.tei.id_extractor.PathIdManager._
import weco.catalogue.tei.id_extractor.models.{TeiIdChangeMessage, TeiIdDeletedMessage, TeiIdMessage}

import java.time.ZonedDateTime
import scala.util.{Success, Try}

class PathIdManager[Dest](pathIds: PathIdTable,
                    store: Writable[S3ObjectLocation, String],
                    messageSender: MessageSender[Dest],
                          bucket: String) {


  def handlePathChanged(pathId: PathId, blobContent: String): Try[Unit] = {
    DB localTx { implicit session =>
          for {
            idRow <- selectById(pathIds, pathId.id)
            pathRow <- selectByPath(pathIds, pathId.path)

            _ <- (idRow, pathRow) match {
              case (Some(row), None) =>
                update(pathId, blobContent, row)
              case (None, None) =>
                insert(pathId, blobContent)
              case (None, Some(row)) =>
                deleteOldIdInsertNewId(pathId, blobContent, row)
              case (Some(idRow), Some(pathRow)) =>
                if (idRow == pathRow) {
                  update(pathId, blobContent, idRow)
                }
                else {
                  deleteOldIdUpdateNewId(pathId, blobContent, idRow, pathRow)
                }
            }
          } yield ()

      }
    }


  def handlePathDeleted(path: String, timeDeleted: ZonedDateTime): Try[Unit] = DB localTx { implicit session =>
        for {
          maybePathId <- selectByPath(pathIds, path)

          _ <- maybePathId match {
            case Some(existingPathId) =>
              delete(path, timeDeleted, maybePathId, existingPathId)
            case None => Success(())
          }
        } yield ()
      }

  private def deleteOldIdUpdateNewId(pathId: PathId, blobContent: String, idRow: PathId, pathRow: PathId)(implicit session: DBSession): Try[Unit] =
    if (pathId.timeModified.isAfter(idRow.timeModified) && pathId.timeModified.isAfter(pathRow.timeModified)) {
      for {
        _ <- storeAndSendChange(pathId, blobContent)
        _ <- sendDeleted(pathRow.copy(timeModified = pathId.timeModified))
        _ <- deletePathId(pathIds, pathId.path)
        _ <- updatePathId(pathIds, pathId)
      } yield ()
    }
    else Success(())

  private def deleteOldIdInsertNewId(pathId: PathId, blobContent: String, storedPathId: PathId)(implicit session: DBSession): Try[Unit] =
    if (pathId.timeModified.isAfter(storedPathId.timeModified)) {
      for {
        _ <- storeAndSendChange(pathId, blobContent)
        _ <- sendDeleted(storedPathId.copy(timeModified = pathId.timeModified))
        _ <- deletePathId(pathIds, pathId.path)
        _ <- insertPathId(pathIds, pathId)
      }
      yield ()
    }
    else Success(())

  private def delete(path: String, timeDeleted: ZonedDateTime, maybePathId: Option[PathId], existingPathId: PathId)(implicit session: DBSession) =
    if (timeDeleted.isAfter(existingPathId.timeModified)) {
      for {
        _ <- sendDeleted(PathId(path, maybePathId.get.id, timeDeleted))
        _ <- deletePathId(pathIds, path)
      } yield ()
    }
    else Success(())

  private def insert(pathId: PathId, blobContent: String)(implicit session: DBSession)  = for {
    _ <- storeAndSendChange(pathId, blobContent)
    - <- insertPathId (pathIds, pathId)
  } yield ()

  private def update(pathId: PathId, blobContent: String, storedPathId: PathId)(implicit session: DBSession) =
    if (pathId.timeModified.isAfter(storedPathId.timeModified)) {
      for {
        _ <- storeAndSendChange(pathId, blobContent)
        _ <- updatePathId(pathIds, pathId)
      } yield ()
    }
    else Success(())



  private def sendDeleted(pathId: PathId)= messageSender.sendT[TeiIdMessage](TeiIdDeletedMessage(pathId.id, pathId.timeModified))

  private def storeAndSendChange(pathId: PathId, blobContent: String) = for {
    stored <-storeTei(pathId, blobContent)
    _ <- messageSender.sendT[TeiIdMessage](TeiIdChangeMessage(pathId.id, stored.id, pathId.timeModified))
  }yield ()

  private def storeTei(pathId: PathId, blobContent: String) = store.put(S3ObjectLocation(bucket, s"tei_files/${pathId.id}/${pathId.timeModified.toEpochSecond}.xml"))(blobContent).left.map(error => error.e).toTry

}

object PathIdManager {

  def selectById(pathIds: PathIdTable,id: String)(implicit session: DBSession) = Try(withSQL {
    select.from(pathIds as pathIds.p).where.eq(pathIds.p.id, id).forUpdate
  }.map(PathId(pathIds.p)).single().apply())

  def selectByPath(pathIds: PathIdTable,path: String)(implicit session: DBSession) = Try(withSQL {
    select.from(pathIds as pathIds.p).where.eq(pathIds.p.path, path).forUpdate
  }.map(PathId(pathIds.p)).single().apply())

  def updatePathId(pathIds: PathIdTable, pathId: PathId)(implicit session: DBSession) =
  Try(withSQL {
    update(pathIds).set(
      pathIds.column.path -> pathId.path,
      pathIds.column.timeModified -> pathId.timeModified.toInstant.toEpochMilli
    ).where.eq(pathIds.column.id, pathId.id)
  }.update.apply())

  def insertPathId(pathIds: PathIdTable, pathId: PathId)(implicit session: DBSession) =
    Try(withSQL {
      insert
        .into(pathIds)
        .namedValues(
          pathIds.column.path -> pathId.path,
          pathIds.column.id -> pathId.id,
          pathIds.column.timeModified -> pathId.timeModified.toInstant.toEpochMilli
        )
    }.update.apply())

  def deletePathId(pathIds: PathIdTable,path: String)(implicit session: DBSession) = Try(withSQL {
    delete.from(pathIds).where.eq(pathIds.column.path, path)
  }.update().apply())
}
