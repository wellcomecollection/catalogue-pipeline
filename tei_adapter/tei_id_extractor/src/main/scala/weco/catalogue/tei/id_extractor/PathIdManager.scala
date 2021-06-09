package weco.catalogue.tei.id_extractor

import java.time.ZonedDateTime
import scala.concurrent.{Future, blocking}
import scala.concurrent.ExecutionContext.Implicits._
import scalikejdbc._
import scalikejdbc.TxBoundary.Future._

class PathIdManager(pathIds: PathIdTable) {


  def handlePathChanged(path: String, id: String, timeModified: ZonedDateTime): Future[Option[PathId]] = {
    DB localTx { implicit session =>
      Future {
        val idRow = blocking {
          withSQL {
            select.from(pathIds as pathIds.p).where.eq(pathIds.p.id, id).forUpdate
          }.map(PathId(pathIds.p)).single().apply()
        }

        idRow match {
          case Some(row) =>
            if(row.timeModified.isAfter(timeModified)){
              None
            }
            else {
              blocking {
                withSQL {
                  update(pathIds).set(
                    pathIds.column.path -> path,
                    pathIds.column.timeModified -> timeModified.format(PathId.formatter)
                  ).where.eq(pathIds.column.id, row.id)
                }.update.apply()
              }
              Some(PathId(path, id, timeModified))
            }
          case None => blocking {
            withSQL {
              insert
                .into(pathIds)
                .namedValues(
                  pathIds.column.path -> path,
                  pathIds.column.id -> id,
                  pathIds.column.timeModified -> timeModified.format(PathId.formatter)
                )
            }.update.apply()

          }
            Some(PathId(path, id, timeModified))
        }
      }
    }
  }


  def handlePathDeleted(path: String, timeDeleted: ZonedDateTime): Future[Option[PathId]] = DB localTx { implicit session =>
    Future {
      val maybePathId = blocking {
        withSQL {
          select.from(pathIds as pathIds.p).where.eq(pathIds.p.path, path).forUpdate
        }.map(PathId(pathIds.p)).single().apply()
      }
      maybePathId match {
        case Some(existingPathId) =>
          if (timeDeleted.isAfter(existingPathId.timeModified)) {
            blocking {
              withSQL {
                delete.from(pathIds).where.eq(pathIds.column.path, path)
              }.update().apply()
            }
            Some(PathId(path, maybePathId.get.id, timeDeleted))
          }
          else {
            None
          }
        case None => None
      }
    }
  }


}
