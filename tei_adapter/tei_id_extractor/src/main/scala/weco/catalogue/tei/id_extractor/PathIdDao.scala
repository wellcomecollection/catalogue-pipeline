package weco.catalogue.tei.id_extractor

import grizzled.slf4j.Logging

import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future, blocking}
import scalikejdbc._


class PathIdDao(pathIds: PathIdTable) extends Logging {
  def getByPath(path: String)(implicit session: DBSession = AutoSession, ec: ExecutionContext): Future[Option[PathId]] = Future {
    blocking {
      withSQL {
        select.from(pathIds as pathIds.p).where.eq(pathIds.p.path, path)
      }.map(PathId(pathIds.p)).single().apply()}}

  def save(id: String, path: String, timeModified: ZonedDateTime)(implicit session: DBSession = AutoSession, ec: ExecutionContext): Future[Unit] = {
    Future {
      blocking {
        withSQL {
          insert
            .into(pathIds)
            .namedValues(
              pathIds.column.path -> path,
              pathIds.column.id -> id,
              pathIds.column.timeModified -> timeModified.format( PathId.formatter)
            )
        }.update.apply()
      }
      ()
    }.recoverWith{
      case e: Throwable =>
        error(e)
        throw(e)
    }
  }

}

